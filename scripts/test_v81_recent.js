'use strict';
const assert = require('assert');
function cleanTransactionText(v){if(typeof v!=='string')return'';return v.replace(/<[^>]*>/g,'').replace(/\[?\s*object\s+HTML[A-Za-z]*Element\s*\]?/gi,'').replace(/\[?\s*object\s+Object\s*\]?/gi,'').replace(/ObjectHTML(?:Input|TextArea|Select|Button|Form)?Element/gi,'').replace(/HTML(?:Input|TextArea|Select|Button|Form)?Element/gi,'').trim()}
function normalizeTransaction(x,index){let d=/^\d{4}-\d{2}-\d{2}$/.test(String(x.date||''))?x.date:new Date().toISOString().slice(0,10),time=/^\d{2}:\d{2}$/.test(String(x.time||''))?x.time:'12:00';return {...x,id:x.id??('recovered_'+index),date:d,time}}
function transactionTimestamp(x){let date=cleanTransactionText(String(x&&x.date||'')),time=cleanTransactionText(String(x&&x.time||'00:00')),m=/^(\d{4})-(\d{2})-(\d{2})$/.exec(date),t=/^(\d{1,2}):(\d{2})(?::(\d{2}))?$/.exec(time);if(!m||!t)return 0;let stamp=new Date(Number(m[1]),Number(m[2])-1,Number(m[3]),Number(t[1]),Number(t[2]),Number(t[3]||0)).getTime();return Number.isFinite(stamp)?stamp:0}
global.date={toString:()=> '[object HTMLInputElement]'};
const fixed=normalizeTransaction({id:'x',date:'2026-10-07',time:'11:11',kind:'income',amount:1},0);
assert.strictEqual(fixed.date,'2026-10-07');
assert.ok(!String(fixed.date).includes('HTMLInputElement'));
const data=[
 {id:'same-newer-entry',date:'2026-10-07',time:'12:00',kind:'expense'},
 {id:'older-income',date:'2026-10-06',time:'23:59',kind:'income'},
 {id:'newest-expense',date:'2026-10-07',time:'18:30',kind:'expense'},
 {id:'same-later-entry',date:'2026-10-07',time:'12:00',kind:'income'}
];
const ids=data.map((item,index)=>({item,index,stamp:transactionTimestamp(item)})).sort((a,b)=>b.stamp-a.stamp||a.index-b.index).map(x=>x.item.id);
assert.deepStrictEqual(ids,['newest-expense','same-newer-entry','same-later-entry','older-income']);
console.log('PASS V81: DOM date collision fixed; recent transactions newest-to-oldest independent of kind.');
