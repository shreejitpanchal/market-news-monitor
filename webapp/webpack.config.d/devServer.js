// Kotlin/JS's documented webpack-override mechanism: every *.js file here
// is merged into the generated webpack config automatically, with `config`
// already in scope. Pins the dev server port so scripts/run_web.ps1/.sh and
// server/'s CORS allowlist can hardcode the same URL instead of parsing
// Gradle's console output for whatever port webpack happened to choose.
//
// Serves over HTTPS with a self-signed localhost cert
// (scripts/gen_dev_cert.sh writes it to webapp/certs/, gitignored — not a
// real secret, but no private key belongs in git). Chrome will flag it as
// untrusted the first visit; click through once, that's expected for a
// self-signed dev cert. If the cert files are missing (fresh clone, never
// ran gen_dev_cert.sh), falls back to plain HTTP rather than failing to
// start at all.
const fs = require("fs");
const path = require("path");

const certDir = path.resolve(__dirname, "../certs");
const keyPath = path.join(certDir, "localhost-key.pem");
const certPath = path.join(certDir, "localhost-cert.pem");

config.devServer = {
    ...config.devServer,
    port: 19001,
    open: true,
};

if (fs.existsSync(keyPath) && fs.existsSync(certPath)) {
    config.devServer.server = {
        type: "https",
        options: {
            key: fs.readFileSync(keyPath),
            cert: fs.readFileSync(certPath),
        },
    };
} else {
    console.warn(
        "[webapp] No dev cert at " + certDir + " -- run scripts/gen_dev_cert.sh for HTTPS. Falling back to HTTP.",
    );
}
