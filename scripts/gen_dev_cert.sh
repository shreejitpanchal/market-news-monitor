#!/usr/bin/env bash
# Generates the self-signed cert the webapp dev server uses for HTTPS
# (webapp/webpack.config.d/devServer.js). Not committed (webapp/certs/ is
# gitignored) -- regenerate it here rather than hand-authoring it again,
# same reasoning as this repo's other generated artifacts.
#
# Self-signed means Chrome will flag it as untrusted the first time you
# visit https://localhost:19001 -- click through the warning (or add an
# exception) once; that's expected for a local dev cert, not a bug.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CERT_DIR="$REPO_ROOT/webapp/certs"

mkdir -p "$CERT_DIR"

# MSYS_NO_PATHCONV avoids git-bash mangling "/CN=localhost" into a Windows path.
MSYS_NO_PATHCONV=1 openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout "$CERT_DIR/localhost-key.pem" \
    -out "$CERT_DIR/localhost-cert.pem" \
    -days 825 \
    -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

echo "Wrote $CERT_DIR/localhost-key.pem and localhost-cert.pem (valid 825 days)."
