// Check if the RLS is hiding enrollments - query from Supabase with service_role key in raw SQL
// Also test the exact function calls the app makes

const SUPABASE_URL = "https://vtpguytfeqbpysxbppyv.supabase.co"
const ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ0cGd1eXRmZXFicHlzeGJwcHl2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ4MTUyOTgsImV4cCI6MjA5MDM5MTI5OH0.nB5StGVR5j1W6nuh-D-RXCEmEYeNypnq9CTTunyPqcA"

const STUDENT_ID = "0ca87e62-534b-4291-8eee-43cafd096bb4"  // chef
const TEACHER_ID = "2dc676a2-8cb7-41c4-9fd0-591cdda0de58"   // shraddha khapra / apna college

const headers = {
  "apikey": ANON_KEY,
  "Authorization": `Bearer ${ANON_KEY}`,
};

async function main() {
  // 1. Check all enrollments (no filter) - if RLS blocks anon, this returns []
  const r1 = await fetch(`${SUPABASE_URL}/rest/v1/enrollments?select=id,student_id,teacher_id,status`, { headers });
  console.log("\n=== ALL ENROLLMENTS (anon, no filter) ===");
  console.log(JSON.stringify(await r1.json(), null, 2));

  // 2. Check for enrollment with the specific student/teacher
  const r2 = await fetch(
    `${SUPABASE_URL}/rest/v1/enrollments?select=id,student_id,teacher_id,status&student_id=eq.${STUDENT_ID}&teacher_id=eq.${TEACHER_ID}`,
    { headers }
  );
  console.log("\n=== ENROLLMENT chef + shraddha ===");
  console.log(JSON.stringify(await r2.json(), null, 2));

  // 3. Check enrollment RLS policy by calling RPC
  const r3 = await fetch(`${SUPABASE_URL}/rest/v1/rpc/get_enrollment_count`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/json" },
    body: JSON.stringify({})
  });
  console.log("\n=== RPC enrollment count (if exists) ===");
  console.log(await r3.text());

  // 4. Check the payment_orders table (anon key restricted by RLS?)
  const r4 = await fetch(`${SUPABASE_URL}/rest/v1/payment_orders?select=id,cashfree_order_id,status&limit=10`, { headers });
  console.log("\n=== PAYMENT ORDERS (anon) ===");
  console.log(JSON.stringify(await r4.json(), null, 2));
  
  // 5. Check if the student's enrollment exists anywhere by querying profiles linked to teacher
  const r5 = await fetch(
    `${SUPABASE_URL}/rest/v1/teachers?select=id,academy_name,join_code&id=eq.${TEACHER_ID}`,
    { headers }
  );
  console.log("\n=== TEACHER join code ===");
  console.log(JSON.stringify(await r5.json(), null, 2));
}

main().catch(console.error);
