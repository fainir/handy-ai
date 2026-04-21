# Tiny static file server for the Handy AI landing page.
# Caddy 2 alpine — listens on fixed port 8080 which the Railway domain targets.
FROM caddy:2-alpine

WORKDIR /srv

COPY . /srv/

# Minimal Caddyfile: listen on 8080, serve /srv, auto-index.html for directories.
# Railway sets PORT; we ignore it and use a fixed 8080 (our target port).
RUN printf ':8080\n\
encode gzip zstd\n\
root * /srv\n\
file_server {\n\
    index index.html\n\
}\n' > /etc/caddy/Caddyfile

EXPOSE 8080

CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
