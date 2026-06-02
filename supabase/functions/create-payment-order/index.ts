import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.3'

const RAZORPAY_KEY_ID = Deno.env.get('RAZORPAY_KEY_ID') || ''
const RAZORPAY_KEY_SECRET = Deno.env.get('RAZORPAY_KEY_SECRET') || ''

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_ANON_KEY') ?? '',
      { global: { headers: { Authorization: req.headers.get('Authorization')! } } }
    )

    const { data: { user }, error: authError } = await supabaseClient.auth.getUser()
    if (authError || !user) throw new Error('Unauthorized')

    const { student_id, teacher_id, amount, month_key } = await req.json()

    if (!student_id || !teacher_id || !amount) {
      throw new Error('Missing required parameters')
    }
    
    // amount is passed in INR (rupees), Razorpay needs paise
    const amountInPaise = amount * 100

    // Fetch teacher's linked account ID
    const { data: teacher, error: teacherError } = await supabaseClient
      .from('teachers')
      .select('razorpay_account_id, academy_name')
      .eq('id', teacher_id)
      .single()

    if (teacherError || !teacher?.razorpay_account_id) {
      throw new Error('Teacher has not connected a bank account')
    }

    // Call Razorpay to create Order with Route transfer
    const authHeader = `Basic ${btoa(`${RAZORPAY_KEY_ID}:${RAZORPAY_KEY_SECRET}`)}`
    
    // We are transferring 100% to the teacher, no platform fee
    const rzpResponse = await fetch('https://api.razorpay.com/v1/orders', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': authHeader,
      },
      body: JSON.stringify({
        amount: amountInPaise,
        currency: 'INR',
        transfers: [
          {
            account: teacher.razorpay_account_id,
            amount: amountInPaise,
            currency: 'INR',
            notes: {
              student_id: student_id,
              month_key: month_key || 'unknown'
            },
            linked_account_notes: ['student_id']
          }
        ]
      })
    })

    const rzpData = await rzpResponse.json()
    if (!rzpResponse.ok) {
      console.error('Razorpay Error:', rzpData)
      throw new Error(`Razorpay Error: ${rzpData.error?.description || 'Failed to create order'}`)
    }

    const orderId = rzpData.id

    // Insert into payment_orders
    const serviceClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    // Look up enrollment_id
    const { data: enrollment } = await serviceClient
      .from('enrollments')
      .select('id')
      .eq('student_id', student_id)
      .eq('teacher_id', teacher_id)
      .single()

    const { error: insertError } = await serviceClient
      .from('payment_orders')
      .insert({
        razorpay_order_id: orderId,
        enrollment_id: enrollment?.id,
        student_id: student_id,
        teacher_id: teacher_id,
        amount: amountInPaise,
        month_key: month_key,
        status: 'created'
      })

    if (insertError) throw insertError

    return new Response(
      JSON.stringify({
        order_id: orderId,
        amount: amount, // INR
        key_id: RAZORPAY_KEY_ID,
        academy_name: teacher.academy_name
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 }
    )
  } catch (error: any) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 400 }
    )
  }
})
