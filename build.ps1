param([switch]$Release, [string]$GradleExecutable = (Join-Path $PSScriptRoot 'gradlew.bat'))
$ErrorActionPreference = 'Stop'
# Windows AIDL writes dependency paths in the local code page: build from an ASCII temporary path.
$stage = Join-Path ([System.IO.Path]::GetTempPath()) ('DualSimTiles-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage | Out-Null
foreach ($file in @('settings.gradle','build.gradle','gradle.properties','local.properties')) {
    if (Test-Path (Join-Path $PSScriptRoot $file)) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $stage }
}
New-Item -ItemType Directory -Path (Join-Path $stage 'app') | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app/build.gradle') -Destination (Join-Path $stage 'app')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app/src') -Destination (Join-Path $stage 'app/src') -Recurse
$tasks = @('assembleDebug','testDebugUnitTest','lintDebug')
if ($Release) { $tasks += 'bundleRelease' }
& $GradleExecutable -p $stage @tasks --console=plain
if ($LASTEXITCODE -ne 0) { throw "构建失败。临时工程：$stage" }
$artifacts = Join-Path $PSScriptRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null
Copy-Item -LiteralPath (Join-Path $stage 'app/build/outputs/apk/debug/app-debug.apk') -Destination $artifacts
Copy-Item -LiteralPath (Join-Path $stage 'app/build/reports') -Destination (Join-Path $artifacts ('reports-' + [guid]::NewGuid().ToString('N'))) -Recurse
if ($Release) { Copy-Item -LiteralPath (Join-Path $stage 'app/build/outputs/bundle/release/app-release.aab') -Destination $artifacts }
Write-Host "输出：$artifacts；临时工程：$stage"
