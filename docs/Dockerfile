# Tiny static file server for the Handy AI landing page.
# Build context is the repo root; this file lives at docs/Dockerfile.
FROM caddy:2-alpine

WORKDIR /srv
COPY docs/ /srv/

RUN ls -la /srv/
RUN printf ':8080\nroot * /srv\nfile_server\n' > /etc/caddy/Caddyfile

EXPOSE 8080
CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
