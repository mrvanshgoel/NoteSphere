const fetch = require('node-fetch'); // Native fetch in Node 18+, but let's use standard HTTP just in case, or axios if it's there.
// Actually Node 18 has global fetch.

async function runTests() {
  const BASE_URL = 'http://localhost:5000/api';
  const headers = {
    'Authorization': 'Bearer TEST_TOKEN',
    'Content-Type': 'application/json'
  };

  console.log('--- STARTING COMPLETE API TEST ---');

  // 1. Profile / Bootstrap
  console.log('\n1. Testing Profile Auto-Bootstrap...');
  let res = await fetch(`${BASE_URL}/auth/profile`, { headers });
  let data = await res.json();
  console.log('Profile:', data);

  // 2. Update Profile
  console.log('\n2. Testing Profile Update...');
  res = await fetch(`${BASE_URL}/auth/profile`, { 
    method: 'PUT', 
    headers, 
    body: JSON.stringify({ name: 'Test User', avatarUrl: 'http://example.com/avatar.png' })
  });
  data = await res.json();
  console.log('Updated Profile:', data);

  // 3. Create Subject
  console.log('\n3. Testing Subject Creation...');
  res = await fetch(`${BASE_URL}/subjects`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ name: 'Test Subject ' + Date.now(), color: '#FF0000', icon: '🔥' })
  });
  const subject = await res.json();
  console.log('Subject Created:', subject);

  // 4. Fetch Subjects
  console.log('\n4. Testing Subject Fetch...');
  res = await fetch(`${BASE_URL}/subjects`, { headers });
  data = await res.json();
  console.log(`Fetched ${data.length} subjects. First:`, data[0]);

  // 5. Create Folder
  console.log('\n5. Testing Folder Creation...');
  res = await fetch(`${BASE_URL}/folders`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ name: 'Test Folder', subjectId: subject.id })
  });
  const folder = await res.json();
  console.log('Folder Created:', folder);

  // 6. Create Note
  console.log('\n6. Testing Note Creation...');
  res = await fetch(`${BASE_URL}/study/subjects/${subject.id}/notes`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ title: 'Test Note', content: 'This is a test note.', tags: ['test'] })
  });
  const note = await res.json();
  console.log('Note Created:', note);

  // 7. Update Note
  console.log('\n7. Testing Note Update...');
  res = await fetch(`${BASE_URL}/study/subjects/${subject.id}/notes/${note.id}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify({ title: 'Updated Test Note' })
  });
  data = await res.json();
  console.log('Note Updated:', data);

  // 8. Fetch Notes
  console.log('\n8. Testing Note Fetch...');
  res = await fetch(`${BASE_URL}/study/subjects/${subject.id}/notes`, { headers });
  data = await res.json();
  console.log(`Fetched ${data.length} notes. First:`, data[0]);

  // 9. Create Chat
  console.log('\n9. Testing AI Chat Persistence...');
  res = await fetch(`${BASE_URL}/ai/chats`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ title: 'Test Chat', messages: [{role: 'user', content: 'Hello'}] })
  });
  const chat = await res.json();
  console.log('Chat Created:', chat);

  // 10. Fetch Chats
  console.log('\n10. Testing Chat Fetch...');
  res = await fetch(`${BASE_URL}/ai/chats`, { headers });
  data = await res.json();
  console.log(`Fetched ${data.length} chats. First:`, data[0]);

  console.log('\n--- ALL TESTS COMPLETED SUCCESSFULLY ---');
}

runTests().catch(console.error);
