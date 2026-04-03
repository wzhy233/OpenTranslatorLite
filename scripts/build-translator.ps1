$ErrorActionPreference = "Stop"

$silent = $false
foreach ($arg in $args) {
    if ($arg -eq "--silent" -or $arg -eq "-silent") {
        $silent = $true
    } else {
        throw "Unknown argument: $arg"
    }
}

$mavenArgs = @("clean", "package")
if ($silent) {
    $mavenArgs += "-Psilent-build"
} else {
    $mavenArgs += "-Pfull-build"
}

& mvn @mavenArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
