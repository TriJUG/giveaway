export function isAdmin(request: Request, env: { ADMIN_TOKEN: string }): boolean {
  const auth = request.headers.get('Authorization');
  if (!auth) return false;
  const [scheme, token] = auth.split(' ');
  return scheme === 'Bearer' && token === env.ADMIN_TOKEN;
}

export function unauthorized(): Response {
  return new Response(JSON.stringify({ error: 'Unauthorized' }), {
    status: 401,
    headers: { 'Content-Type': 'application/json' },
  });
}
