import { getText } from "./wslib.mjs";
const D = "०१२३४५६७८९"; const dev = (n) => [...String(n)].map(d => D[d]).join("");
const ranges = [[601,900],[901,1200],[1201,1500],[1501,1800],[1801,2100],[2101,2400],[2401,2700],[2701,3000],[3001,3300],[3301,3600],[3601,3900],[3901,4200],[4201,4583]];
let all = "";
for (const [a,b] of ranges) { const t = await getText(`तुकाराम गाथा/गाथा ${dev(a)} ते ${dev(b)}`); all += t.slice(t.indexOf("}}")+2) + "\n"; }
const show = (re, n=8) => { const out=[]; let m; const r = new RegExp(re,"g"); while ((m = r.exec(all)) && out.length<n) out.push(all.slice(Math.max(0,m.index-12), m.index+14).replace(/\n/g,"⏎")); return out.join("\n   "); };
for (const [name, re] of [["U+0086","\u0086"],["ZWJ","\u200d"],["LRM","\u200e"],["I","I"],["N","N"],["h","h"],["j","j"],["b","b"],["[","\["],["{","\{"],["ॉ","ॉ"],["Y not followed by ा","Y(?!ा)"],["H after vowel-ish","[ािीुूेैोौ]H"]]) console.log(name, "\n   "+show(re));
