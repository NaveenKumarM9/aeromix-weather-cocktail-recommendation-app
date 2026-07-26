$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$runtimeRoot = Join-Path $projectRoot ".runtime"
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
$needsJava = $true

if ($javaCommand) {
    $versionProcessInfo = New-Object System.Diagnostics.ProcessStartInfo
    $versionProcessInfo.FileName = $javaCommand.Source
    $versionProcessInfo.Arguments = "-version"
    $versionProcessInfo.UseShellExecute = $false
    $versionProcessInfo.RedirectStandardOutput = $true
    $versionProcessInfo.RedirectStandardError = $true
    $versionProcess = [System.Diagnostics.Process]::Start($versionProcessInfo)
    $versionOutput = $versionProcess.StandardError.ReadToEnd() +
        $versionProcess.StandardOutput.ReadToEnd()
    $versionProcess.WaitForExit()
    $versionText = ($versionOutput -split "\r?\n")[0]
    if ($versionText -match '"(\d+)') {
        $needsJava = [int]$Matches[1] -lt 25
    }
}

if ($needsJava) {
    Write-Host "Preparing Java 25 LTS for this project (one-time setup)..."
    New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
    $jdkHome = Get-ChildItem -LiteralPath $runtimeRoot -Directory -Filter "jdk-25*" |
        Select-Object -First 1
    if (-not $jdkHome) {
        $archive = Join-Path $runtimeRoot "jdk25.zip"
        $archiveUri = "https://download.oracle.com/java/25/latest/jdk-25_windows-x64_bin.zip"
        $checksumUri = "$archiveUri.sha256"
        $expectedChecksum = (Invoke-RestMethod -Uri $checksumUri).Trim().Split()[0].ToLowerInvariant()
        if (Test-Path -LiteralPath $archive) {
            $cachedChecksum = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($cachedChecksum -ne $expectedChecksum) {
                Remove-Item -LiteralPath $archive
            }
        }
        if (-not (Test-Path -LiteralPath $archive)) {
            Invoke-WebRequest -Uri $archiveUri -OutFile $archive
        }
        $actualChecksum = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualChecksum -ne $expectedChecksum) {
            throw "The downloaded Java archive failed checksum verification."
        }
        Expand-Archive -LiteralPath $archive -DestinationPath $runtimeRoot
        $jdkHome = Get-ChildItem -LiteralPath $runtimeRoot -Directory -Filter "jdk-25*" |
            Select-Object -First 1
    }
    $env:JAVA_HOME = $jdkHome.FullName
    $env:Path = "$($jdkHome.FullName)\bin;$env:Path"
}

Set-Location -LiteralPath $projectRoot
Write-Host ""
Write-Host "Aeromix is starting at http://localhost:8080"
Write-Host "Press Ctrl+C to stop it."
Write-Host ""
& ".\mvnw.cmd" spring-boot:run
