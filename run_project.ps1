# NetTacToe: Build, Test, and Run (PowerShell)
# Usage:
#   Right-click > Run with PowerShell OR
#   From terminal:  .\run_project.ps1

param(
    [switch]$NoTests,
    [int]$Clients = 2
)

$ErrorActionPreference = 'Stop'

function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "[ OK ] $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Err($msg)  { Write-Host "[ERR ] $msg" -ForegroundColor Red }

# 0) Go to script directory (project root)
Set-Location $PSScriptRoot
Info "Working directory: $(Get-Location)"

# 1) Clean previous build
Info "Cleaning out/"
Remove-Item -Recurse -Force .\out -ErrorAction Ignore
New-Item -ItemType Directory -Path .\out | Out-Null

# 2) Compile all sources
Info "Compiling sources..."
$files = Get-ChildItem -Path src -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
if (-not $files -or $files.Count -eq 0) { Err "No .java files found under src/"; exit 1 }
javac -d out -sourcepath src $files
if ($LASTEXITCODE -ne 0) { Err "Compilation failed"; exit 1 } else { Ok "Compilation succeeded" }

# 3) Verify some key classes
Get-ChildItem -Recurse out -Filter GameServer.class | Out-Null
Get-ChildItem -Recurse out -Filter GameClient.class | Out-Null
Get-ChildItem -Recurse out -Filter BoardTest.class | Out-Null
Ok "Key classes found"

# 4) Run tests (optional)
if (-not $NoTests) {
  Info "Running tests..."
  java -cp out test.BoardTest
  if ($LASTEXITCODE -ne 0) { Err "BoardTest failed"; exit 1 }
  java -cp out test.BoardSerializationTest
  if ($LASTEXITCODE -ne 0) { Err "BoardSerializationTest failed"; exit 1 }
  java -cp out test.ClientDeserializationTest
  if ($LASTEXITCODE -ne 0) { Err "ClientDeserializationTest failed"; exit 1 }
  Ok "Tests passed"
} else {
  Warn "Skipping tests (--NoTests)"
}

# 5) Start server in a new window
Info "Starting server (new window)"
Start-Process -FilePath "java" -ArgumentList "-cp out server.GameServer"
Start-Sleep -Seconds 2

# 6) Start clients
for ($i = 1; $i -le $Clients; $i++) {
  Info "Starting client $i"
  Start-Process -FilePath "java" -ArgumentList "-cp out client.GameClient"
  Start-Sleep -Milliseconds 700
}

Ok "Launched server and $Clients client(s)."
Write-Host "\nNext steps:" -ForegroundColor Magenta
Write-Host "  - Enter a name in each client and click Connect" -ForegroundColor Magenta
Write-Host "  - Play a round; click 'View Leaderboard' to see standings" -ForegroundColor Magenta
Write-Host "  - To adjust client count: .\run_project.ps1 -Clients 2" -ForegroundColor Magenta
