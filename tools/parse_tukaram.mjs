import { getText } from "./wslib.mjs";
const D = "०१२३४५६७८९";
const dev = (n) => [...String(n)].map(d => D[d]).join("");
const ranges = [[1,300],[301,600],[601,900],[901,1200],[1201,1500],[1501,1800],[1801,2100],[2101,2400],[2401,2700],[2701,3000],[3001,3300],[3301,3600],[3601,3900],[3901,4200],[4201,4583]];
const toNum = (s) => Number([...s].map(c => D.indexOf(c) >= 0 ? D.indexOf(c) : c).join(""));
export async function parseTukaram() {
  const out = []; const headings = [];
  let section = "";
  for (const [a, b] of ranges) {
    const t = await getText(`तुकाराम गाथा/गाथा ${dev(a)} ते ${dev(b)}`);
    const m = t.match(/<poem>([\s\S]*?)<\/poem>/g) || [];
    const body = m.map(x => x.replace(/<\/?poem>/g, "")).join("\n");
    const lines = body.split(/\r?\n/).map(l => l.trim());
    let cur = null;
    for (const l of lines) {
      if (!l) continue;
      const num = l.match(/^([०-९]+)\s*$/);
      if (num) { if (cur) out.push(cur); cur = { n: toNum(num[1]), section, lines: [] }; continue; }
      const isVerse = /[।॥]/.test(l) || /^[^ ]+ [^ ]+ [^ ]+/.test(l) && /[॥]/.test(l);
      if (!isVerse && /[-–—]/.test(l) && l.length < 80 && !/[।॥]/.test(l)) { section = l; headings.push([cur?.n ?? 0, l]); if (cur) { out.push(cur); cur = null; } continue; }
      if (cur) cur.lines.push(l);
      else headings.push([0, "?? " + l]);
    }
    if (cur) out.push(cur);
  }
  return { out, headings };
}
if (process.argv[1].endsWith("parse_tukaram.mjs")) {
  const { out, headings } = await parseTukaram();
  console.log("abhangs", out.length, "headings", headings.length);
  console.log(headings.slice(0, 70).map(h => h.join("\t")).join("\n"));
  const nums = out.map(x => x.n); const miss = []; for (let i = 1; i <= 4583; i++) if (!nums.includes(i)) miss.push(i);
  console.log("missing", miss.length, miss.slice(0, 40).join(","));
  console.log(JSON.stringify(out[1]), JSON.stringify(out[4000]));
}
