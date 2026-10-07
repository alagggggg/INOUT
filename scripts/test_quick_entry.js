'use strict';
const assert = require('assert');
const categories = [
  {id:'salary',kind:'income'}, {id:'bonus',kind:'income'},
  {id:'groceries',kind:'expense'}, {id:'transport',kind:'expense'}
];
let data = [];
let nativeQuickSequence = 0;
function cleanTransactionText(v) {
  if (typeof v !== 'string') return '';
  return v.replace(/<[^>]*>/g,'')
    .replace(/\[object HTML[^\]]*\]/gi,'')
    .replace(/\[object Object\]/gi,'')
    .replace(/ObjectHTML(?:InputElement|Element)?/gi,'').trim();
}
function save(payloadText, nowMs = Date.now(), randomText = 'abcde') {
  try {
    const p = JSON.parse(String(payloadText || '{}'));
    const kind = p.kind === 'income' ? 'income' : 'expense';
    const categoryId = cleanTransactionText(p.categoryId);
    const noteText = cleanTransactionText(p.note);
    const factor = Number(p.factor) || 1;
    const raw = Number(p.raw);
    const actual = raw * factor;
    if (!categories.some(c => c.id === categoryId && c.kind === kind)) return false;
    if (!Number.isFinite(actual) || actual <= 0 || ![1,1000,1000000,1000000000].includes(factor)) return false;
    const id = 'w' + nowMs.toString(36) + (nativeQuickSequence++).toString(36) + randomText;
    data.unshift({id, kind, amount:actual/1000, amountScale:factor, category:categoryId, note:noteText, source:'Widget'});
    return true;
  } catch (_) { return false; }
}
// 200 repeated entries at the same millisecond: IDs still must be unique.
for (let i=0;i<200;i++) {
  const kind = i % 2 ? 'income' : 'expense';
  const categoryId = kind === 'income' ? 'salary' : 'groceries';
  const factor = [1,1000,1000000,1000000000][i % 4];
  assert.strictEqual(save(JSON.stringify({kind,categoryId,raw:i+1,factor,note:`Lần ${i+1}`}), 1700000000000, 'fixed'), true);
}
assert.strictEqual(data.length, 200);
assert.strictEqual(new Set(data.map(x=>x.id)).size, 200);
assert.ok(data.every(x=>x.source === 'Widget'));
assert.ok(data.every(x=>typeof x.source === 'string'));
assert.ok(data.every(x=>!/(object|htmlinputelement)/i.test(x.source+x.note+x.category)));
// Malicious/object-like note text is cleaned, never shown as a tag.
assert.strictEqual(save(JSON.stringify({kind:'expense',categoryId:'transport',raw:50,factor:1000,note:'[object HTMLInputElement]'})), true);
assert.strictEqual(data[0].note, '');
assert.strictEqual(data[0].source, 'Widget');
// Invalid category, type mismatch, amount and factor are rejected without changing data.
const before = data.length;
assert.strictEqual(save(JSON.stringify({kind:'expense',categoryId:'salary',raw:1,factor:1000,note:''})), false);
assert.strictEqual(save(JSON.stringify({kind:'expense',categoryId:'groceries',raw:0,factor:1000,note:''})), false);
assert.strictEqual(save(JSON.stringify({kind:'expense',categoryId:'groceries',raw:1,factor:10,note:''})), false);
assert.strictEqual(save('{bad json'), false);
assert.strictEqual(data.length, before);
console.log(`PASS quick-entry repeated test: ${data.length} valid records, all IDs unique, source clean.`);

// Editing must preserve the original source.
function cleanSource(value) {
  const cleaned = cleanTransactionText(value);
  return /object(?:html| object)|html(?:input)?element/i.test(cleaned) ? '' : cleaned;
}
function sourceForEdit(existing, appLabel = 'Ứng dụng') {
  if (!existing) return appLabel;
  const cleaned = cleanSource(existing.source);
  if (cleaned === 'Widget' || String(existing.id || '').startsWith('w')) return 'Widget';
  return cleaned || appLabel;
}
function editRecord(existing, changes) {
  return {...existing, ...changes, id: existing.id, source: sourceForEdit(existing)};
}
const widgetOriginal = data.find(x => x.source === 'Widget');
const widgetEdited = editRecord(widgetOriginal, {amount:999, note:'Đã sửa', category:'transport', kind:'expense'});
assert.strictEqual(widgetEdited.source, 'Widget');
assert.strictEqual(widgetEdited.id, widgetOriginal.id);
assert.strictEqual(widgetEdited.note, 'Đã sửa');
const excelOriginal = {id:'excel-1', kind:'income', category:'salary', amount:1, note:'', source:'Excel'};
assert.strictEqual(editRecord(excelOriginal, {amount:2}).source, 'Excel');
const appOriginal = {id:'m1', kind:'expense', category:'groceries', amount:1, note:'', source:'Ứng dụng'};
assert.strictEqual(editRecord(appOriginal, {amount:2}).source, 'Ứng dụng');
const brokenWidget = {id:'w-broken', kind:'expense', category:'groceries', amount:1, note:'', source:'[object HTMLInputElement]'};
assert.strictEqual(editRecord(brokenWidget, {amount:2}).source, 'Widget');
const brokenApp = {id:'m-broken', kind:'expense', category:'groceries', amount:1, note:'', source:'[object HTMLInputElement]'};
assert.strictEqual(editRecord(brokenApp, {amount:2}).source, 'Ứng dụng');
console.log('PASS edit-source test: Widget, Excel and App sources preserved; broken sources repaired.');

// V80 DOM-tag rendering regression tests.
const domBadSamples = [
  '[object HTMLInputElement]',
  'object HTMLInputElement',
  '[object HTMLTextAreaElement]',
  'ObjectHTMLInputElement',
  '<input value="bad">',
  '[object Object]'
];
for (const bad of domBadSamples) {
  const cleaned = cleanTransactionText(bad)
    .replace(/\[?\s*object\s+HTML[A-Za-z]*Element\s*\]?/gi,'')
    .replace(/\[?\s*object\s+Object\s*\]?/gi,'')
    .replace(/ObjectHTML(?:Input|TextArea|Select|Button|Form)?Element/gi,'')
    .replace(/HTML(?:Input|TextArea|Select|Button|Form)?Element/gi,'')
    .trim();
  assert.strictEqual(cleaned, '');
}
console.log('PASS V80 DOM-tag regression test: malformed DOM strings are never rendered as transaction notes.');
