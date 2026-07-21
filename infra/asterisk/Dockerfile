FROM debian:bullseye-slim

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       asterisk curl gettext-base sox ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY asterisk.conf /templates/asterisk.conf
COPY pjsip.conf /templates/pjsip.conf
COPY extensions.conf /templates/extensions.conf
COPY http.conf /templates/http.conf
COPY ari.conf /templates/ari.conf
COPY rtp.conf /templates/rtp.conf
COPY entrypoint.sh /entrypoint.sh

RUN sed -i 's/\r$//' /entrypoint.sh \
    && chmod +x /entrypoint.sh \
    && mkdir -p /var/spool/asterisk/recording /var/lib/asterisk/sounds/custom \
    && sox -n -r 8000 -c 1 -b 16 /var/lib/asterisk/sounds/custom/payment-reminder.wav synth 1 sine 880 vol 0.1

EXPOSE 5060/udp 8088/tcp 10000-10100/udp
ENTRYPOINT ["/entrypoint.sh"]
