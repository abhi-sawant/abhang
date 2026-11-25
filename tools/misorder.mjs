import { getText } from "./wslib.mjs";
const D = "०१२३४५६७८९";
const dev = (n) => [...String(n)].map(d => D[d]).join("");
const ranges = [[1,300],[301,600],[601,900],[901,1200],[1201,1500],[1501,1800]];
for (const [a,b] of ranges) {
  const t = await getText(`तुकाराम गाथा/गाथा ${dev(a)} ते ${dev(b)}`); const body = t.slice(t.indexOf("}}")+2);
  const words = body.split(/[\s।॥]+/);
  const m = words.filter(w => /ि[क-ह]्/.test(w));
  const m2 = words.filter(w => /ि[ािीुूेैोौ]/.test(w) || /[ािीुूेैोौ]ि/.test(w) || /ििि|िि/.test(w));
  console.log(a, "ि+cluster words:", m.length, "distinct", new Set(m).size, "| doubled/ill-ordered:", m2.length);
  if (a === 601) { console.log([...new Set(m)].slice(0, 60).join(" ")); console.log("ill:", [...new Set(m2)].slice(0, 40).join(" ")); }
  if (a === 1) { console.log("clean sample:", [...new Set(m)].slice(0, 40).join(" ")); }
}
