# Pre-commit checks: format Kotlin, verify style, compile, then run unit tests.
# Run from project root, or from anywhere (script switches to its own directory).
Set-Location $PSScriptRoot

Write-Host "Running ktlintFormat..." -ForegroundColor Cyan
$format = .\gradlew ktlintFormat --no-daemon 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host $format
    exit $LASTEXITCODE
}

Write-Host "Running ktlintCheck..." -ForegroundColor Cyan
$check = .\gradlew ktlintCheck --no-daemon 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host $check
    exit $LASTEXITCODE
}

Write-Host "Running compileDebugKotlin..." -ForegroundColor Cyan
$compile = .\gradlew compileDebugKotlin --no-daemon 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host $compile
    exit $LASTEXITCODE
}

Write-Host "Running test..." -ForegroundColor Cyan
$test = .\gradlew test --no-daemon 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host $test
    exit $LASTEXITCODE
}

Write-Host "Pre-commit checks completed successfully." -ForegroundColor Green
