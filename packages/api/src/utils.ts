export function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function notFound(msg = 'Not found'): Response {
  return json({ error: msg }, 404);
}

export function badRequest(msg: string): Response {
  return json({ error: msg }, 400);
}

export function conflict(msg: string): Response {
  return json({ error: msg }, 409);
}

export function randomUUID(): string {
  return crypto.randomUUID();
}

export function isValidDate(s: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(s) && !isNaN(Date.parse(s));
}
