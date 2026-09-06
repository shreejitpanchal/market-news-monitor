// Kotlin/JS's documented webpack-override mechanism: every *.js file here
// is merged into the generated webpack config automatically, with `config`
// already in scope. Pins the dev server port so scripts/run_web.ps1/.sh and
// server/'s CORS allowlist can hardcode the same URL instead of parsing
// Gradle's console output for whatever port webpack happened to choose.
config.devServer = {
    ...config.devServer,
    port: 19001,
    open: true,
};
