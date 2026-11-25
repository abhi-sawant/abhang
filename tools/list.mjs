import { prefix } from "./wslib.mjs";
for (const p of process.argv.slice(2)) { console.log("== " + p); console.log((await prefix(p)).join("\n")); }
