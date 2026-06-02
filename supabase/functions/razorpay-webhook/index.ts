import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.3'
import { hmac } from "https://deno.land/x/hmac@v2.0.1/mod.ts"

const RAZORPAY_WEBHOOK_SECRET = Deno.env.get('RAZORPAY_WEBHOOK_SECRET') || ''

serve(async (req) => {
  try {
    const signature = req.headers.get('X-Razorpay-Signature')
    if (!signature) {
      return new Response('Missing signature', { status: 400 })
    }

    const bodyText = await req.text()
    
    // Verify signature
    const expectedSignature = hmac("sha256", RAZORPAY_WEBHOOK_SECRET, bodyText, "utf8", "hex")
    
    if (expectedSignature !== signature) {
      return new Response('Invalid signature', { status: 400 })
    }

    const payload = JSON.parse(bodyText)
    const event = payload.event
    
    const serviceClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )
    
    // Log webhook
    const paymentEntity = payload.payload?.payment?.entity
    const orderId = paymentEntity?.order_id
    const paymentId = paymentEntity?.id
    
    await serviceClient.from('webhook_logs').insert({
      event_type: event,
      razorpay_payment_id: paymentId,
      razorpay_order_id: orderId,
      payload: payload
    })

    if (event === 'payment.captured' && orderId) {
      // Find the order
      const { data: order } = await serviceClient
        .from('payment_orders')
        .select('*')
        .eq('razorpay_order_id', orderId)
        .single()
        
      if (!order || order.status === 'paid') {
        return new Response('Order not found or already paid', { status: 200 })
      }
      
      const paymentMode = paymentEntity?.method || 'Online'
      const amountInINR = order.amount / 100
      const currentMonth = new Date().toISOString().substring(0, 7) // YYYY-MM
      
      // Update order status
      await serviceClient
        .from('payment_orders')
        .update({
          status: 'paid',
          razorpay_payment_id: paymentId,
          updated_at: new Date().toISOString()
        })
        .eq('id', order.id)
        
      // Record payment
      await serviceClient.from('payments').insert({
        enrollment_id: order.enrollment_id,
        student_id: order.student_id,
        teacher_id: order.teacher_id,
        amount: amountInINR,
        payment_mode: paymentMode,
        transaction_id: paymentId,
        month_key: order.month_key || currentMonth
      })
      
      // Update fee records
      const { data: feeRecord } = await serviceClient
        .from('fee_records')
        .select('*')
        .eq('enrollment_id', order.enrollment_id)
        .eq('month_key', order.month_key || currentMonth)
        .single()
        
      if (feeRecord) {
        const currentPaid = feeRecord.paid_amount
        const totalDue = feeRecord.total_amount
        const newTotal = currentPaid + amountInINR
        const regularPayment = Math.min(newTotal, totalDue)
        const excess = Math.max(0, newTotal - totalDue)
        
        let newStatus = 'pending'
        if (regularPayment >= totalDue) newStatus = 'paid'
        else if (regularPayment > 0) newStatus = 'partial'
        
        await serviceClient
          .from('fee_records')
          .update({
            paid_amount: regularPayment,
            status: newStatus
          })
          .eq('id', feeRecord.id)
          
        // Advance balance
        if (excess > 0) {
          const { data: enrollment } = await serviceClient
            .from('enrollments')
            .select('advance_balance')
            .eq('id', order.enrollment_id)
            .single()
            
          if (enrollment) {
            const newAdvance = (enrollment.advance_balance || 0) + excess
            await serviceClient
              .from('enrollments')
              .update({ advance_balance: newAdvance })
              .eq('id', order.enrollment_id)
          }
        }
      }
    }

    return new Response(JSON.stringify({ status: 'ok' }), { status: 200 })
  } catch (error: any) {
    console.error('Webhook Error:', error)
    return new Response(error.message, { status: 500 })
  }
})
