# Tiny static file server for the Handy AI landing page.
# Uses Caddy 2: built-in TLS/HTTP serving, reads PORT from Railway env.
FROM caddy:2-alpine

WORKDIR /srv

# Copy everything in this directory (docs/) into the image.
COPY . /srv/

# Caddyfile — serves / as static files on $PORT (default 8080 locally).
# Railway sets PORT; Caddy substitutes via environment placeholder.
RUN printf ':{$PORT:8080}\n\
encode gzip zstd\n\
root * /srv\n\
# Clean URLs: /privacy -> /privacy.html, /terms -> /terms.html\n\
try_files {path}.html {path} /index.html\n\
file_server\n' > /etc/caddy/Caddyfile

CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
