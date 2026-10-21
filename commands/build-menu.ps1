# Interactive Gradle menu. Run from PowerShell: .\build-menu.ps1
# Run an option directly by its shortcut: .\build-menu.ps1 rebuild

param([string]$Shortcut)

Set-Location $PSScriptRoot
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$env:JAVA_HOME = "C:\Users\RogerPhilippePereira\.jdks\corretto-17.0.20.1"

if (-not (Test-Path $env:JAVA_HOME)) {
    Write-Host "JDK not found: $env:JAVA_HOME" -ForegroundColor Red
    exit 1
}

$options = @(
    @{ Key = "dev"; Label = "Assemble dev debug"; Tasks = @("assembleDevelopmentDebug"); Info = "APK debug do flavor development (incremental)." },
    @{ Key = "rebuild"; Label = "Clean + assemble dev debug"; Tasks = @("clean", "assembleDevelopmentDebug"); Info = "Build completo do zero. Use quando o APK sai inconsistente (NoClassDefFoundError)." },
    @{ Key = "install"; Label = "Install dev on device"; Tasks = @("installDevelopmentDebug"); Info = "Gera se preciso e instala no aparelho conectado (adb)." },
    @{ Key = "prod"; Label = "Assemble prod debug"; Tasks = @("assembleProductionDebug"); Info = "APK debug do flavor production." },
    @{ Key = "prodgp"; Label = "Assemble prodGP debug"; Tasks = @("assembleProductionGPDebug"); Info = "APK debug do flavor productionGP." },
    @{ Key = "debug"; Label = "Assemble all debug"; Tasks = @("assembleDebug"); Info = "APKs debug dos três flavors." },
    @{ Key = "dev-release"; Label = "Assemble dev release"; Tasks = @("assembleDevelopmentRelease"); Info = "APK release do development, ofuscado e sem assinatura." },
    @{ Key = "prod-release"; Label = "Assemble prod release"; Tasks = @("assembleProductionRelease"); Info = "APK release do production, ofuscado e sem assinatura." },
    @{ Key = "prodgp-release"; Label = "Assemble prodGP release"; Tasks = @("assembleProductionGPRelease"); Info = "APK release do productionGP, ofuscado e sem assinatura." },
    @{ Key = "release"; Label = "Assemble all release"; Tasks = @("assembleRelease"); Info = "APKs release dos três flavors, sem assinatura." },
    @{ Key = "clean"; Label = "Clean"; Tasks = @("clean"); Info = "Apaga app/build. O próximo build recompila tudo." },
    @{ Key = "build"; Label = "Build"; Tasks = @("build"); Info = "Compila todas as variantes e roda testes e lint. Demorado." },
    @{ Key = "stop"; Label = "Stop daemons"; Tasks = @("--stop"); Info = "Para todos os daemons. Interrompe build em andamento no Android Studio." }
)

function Show-Menu([int]$selected) {
    Clear-Host
    Write-Host "=== Gradle · $(Split-Path $env:JAVA_HOME -Leaf) ===" -ForegroundColor Cyan
    Write-Host ""
    for ($i = 0; $i -lt $options.Count; $i++) {
        if ($i -eq $selected) {
            Write-Host (" > {0,-28} {1,-15}" -f $options[$i].Label, $options[$i].Key) -ForegroundColor Black -BackgroundColor Cyan
        } else {
            Write-Host ("   {0,-28} " -f $options[$i].Label) -NoNewline
            Write-Host $options[$i].Key -ForegroundColor DarkGray
        }
    }
    $option = $options[$selected]
    Write-Host ""
    Write-Host (" " + ("─" * 60)) -ForegroundColor DarkGray
    Write-Host " gradlew $($option.Tasks -join ' ')" -ForegroundColor Cyan
    Write-Host " $($option.Info)"
    Write-Host ""
    Write-Host " ↑↓ move · Enter run · Esc exit" -ForegroundColor DarkGray
}

function Invoke-Option($option) {
    $tasks = $option.Tasks
    Write-Host "> .\gradlew.bat $($tasks -join ' ')" -ForegroundColor Cyan
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    # Out-Host streams Gradle output to the console instead of the function's return value
    & .\gradlew.bat @tasks | Out-Host
    $script:status = $LASTEXITCODE
    $seconds = [int]$watch.Elapsed.TotalSeconds
    Write-Host ""
    if ($script:status -eq 0) {
        Write-Host "OK in ${seconds}s" -ForegroundColor Green
    } else {
        Write-Host "FAILED (exit $script:status) in ${seconds}s" -ForegroundColor Red
    }
}

if ($Shortcut) {
    $option = $options | Where-Object { $_.Key -eq $Shortcut }
    if (-not $option) {
        Write-Host "Unknown shortcut: $Shortcut" -ForegroundColor Yellow
        Write-Host "Available: $(($options | ForEach-Object { $_.Key }) -join ', ')"
        exit 1
    }
    Invoke-Option $option
    exit $script:status
}

$selected = 0
while ($true) {
    Show-Menu $selected
    $key = [Console]::ReadKey($true)
    switch ($key.Key) {
        "UpArrow" { $selected = ($selected - 1 + $options.Count) % $options.Count }
        "DownArrow" { $selected = ($selected + 1) % $options.Count }
        "Enter" {
            Clear-Host
            Invoke-Option $options[$selected]
            Write-Host ""
            Write-Host "Press any key to return to the menu..." -ForegroundColor DarkGray
            [Console]::ReadKey($true) | Out-Null
        }
        "Escape" {
            Clear-Host
            exit 0
        }
    }
}
