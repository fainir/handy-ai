# Tiny static file server for the Handy AI landing page.
FROM caddy:2-alpine

WORKDIR /srv
COPY . /srv/

# Show what we copied (helps debug if the context is wrong).
RUN ls -la /srv/

RUN printf ':8080\nroot * /srv\nfile_server browse\n' > /etc/caddy/Caddyfile \
 && cat /etc/caddy/Caddyfile

EXPOSE 8080

CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
