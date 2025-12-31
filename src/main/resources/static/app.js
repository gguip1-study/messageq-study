const out = document.querySelector("#out");
const userIdEl = document.querySelector("#userId");
const couponIdEl = document.querySelector("#couponId");

function write(v) {
  out.textContent = typeof v === "string" ? v : JSON.stringify(v, null, 2);
}

async function post(url, body) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  let parsed = text;
  try { parsed = JSON.parse(text); } catch {}
  return { status: res.status, body: parsed };
}

document.querySelector("#syncBtn").onclick = async () => {
  write("동기 발급 요청 중...");
  const body = { userId: Number(userIdEl.value), couponId: Number(couponIdEl.value) };
  write(await post("/api/coupons/issue-sync", body));
};

document.querySelector("#asyncBtn").onclick = async () => {
  write("MQ 발급 접수 중...");
  const body = { userId: Number(userIdEl.value), couponId: Number(couponIdEl.value) };
  write(await post("/api/coupons/issue-async", body));
};
