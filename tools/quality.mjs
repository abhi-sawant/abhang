import { getText } from "./wslib.mjs";
const D = "०१२३४५६७८९";
const dev = (n) => [...String(n)].map(d => D[d]).join("");
const ranges = [[1,300],[301,600],[601,900],[901,1200],[1201,1500],[1501,1800],[1801,2100],[2101,2400],[2401,2700],[2701,3000],[3001,3300],[3301,3600],[3601,3900],[3901,4200],[4201,4583]];
for (const [a,b] of ranges) {
  let t = await getText(`तुकाराम गाथा/गाथा ${dev(a)} ते ${dev(b)}`);
  const body = t.slice(t.indexOf("}}") + 2);
  const cnt = (re) => (body.match(re) || []).length;
  console.log(String(a).padStart(4), "len", body.length, "latinLetters", cnt(/[A-Za-z]/g), "ऩ", cnt(/ऩ/g), "ि-after-virama/vowel-ish", cnt(/[्ा-ौ]ि|ि्/g), "asciiVerse", cnt(/॥\d/g), "devVerse", cnt(/॥[०-९]/g));
}
