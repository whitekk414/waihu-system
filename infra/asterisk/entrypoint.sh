#!/bin/sh
set -eu

: "${ASTERISK_SIP_PASSWORD:=sip-dev-only}"
: "${ASTERISK_ARI_USER:=outbound}"
: "${ASTERISK_ARI_PASSWORD:=outbound-dev-only}"
: "${ASTERISK_EXTERNAL_ADDRESS:=host.docker.internal}"
export ASTERISK_SIP_PASSWORD ASTERISK_ARI_USER ASTERISK_ARI_PASSWORD ASTERISK_EXTERNAL_ADDRESS

for file in asterisk.conf pjsip.conf extensions.conf http.conf ari.conf rtp.conf; do
  envsubst < "/templates/$file" > "/etc/asterisk/$file"
done

exec asterisk -f -vvv
