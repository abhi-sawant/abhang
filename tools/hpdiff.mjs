import { mahiti, chak } from "./haripath.mjs";
import { DevanagariNormalizer as N } from "./_norm.mjs";
const D = "०१२३४५६७८९";
const m = mahiti().filter(x => x.n <= 28);
const c = chak(); const start = c.indexOf("श्री गणेशाय"); const end = c.indexOf("श्रीनामदेवमहाराजकृत हरिपाठाचे अभंग", start + 10);
const sect = c.slice(start, end);
const blocks = sect.split(/\n\s*([०-९]+)\s*\n/).slice(1);
const cm = new Map(); for (let i = 0; i < blocks.length; i += 2) cm.set(Number([...blocks[i]].map(ch => D.indexOf(ch)).join("")), blocks[i+1].split("\n").map(l=>l.trim()).filter(l=>/[।॥]/.test(l)));
console.log("chak blocks", cm.size);
let diffs = 0;
for (const a of m) {
  const A = N.normalize(a.lines.join(" ")).split(" "), B = N.normalize((cm.get(a.n)||[]).join(" ")).split(" ");
  const sa = new Set(B), sb = new Set(A);
  const onlyA = A.filter(w => !sa.has(w)), onlyB = B.filter(w => !sb.has(w));
  if (onlyA.length || onlyB.length) { diffs++; console.log(`#${a.n} mahiti-only: ${onlyA.join(" ")} | chak-only: ${onlyB.join(" ")}`); }
}
console.log("abhangs with differences:", diffs, "/", m.length);
