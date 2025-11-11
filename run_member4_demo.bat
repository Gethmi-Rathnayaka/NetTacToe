# 1) Compile everything into out/
javac -d out -sourcepath src src\**\*.java

# 2) Run board tests (prints to console)
java -cp out test.BoardTest
java -cp out test.BoardSerializationTest
java -cp out test.ClientDeserializationTest

# 3) Start server (opens Server UI)
java -cp out server.GameServer

# 4) Start two clients (each opens a Swing client window)
java -cp out client.GameClient
java -cp out client.GameClient@echo off
echo ===================================================
echo    NetTacToe - Member 4 Implementation Demo
echo ===================================================
echo.
echo This demonstrates Member 4's responsibilities:
echo - Game State Management
echo - Live Board Visualization  
echo - Real-time Board Synchronization
echo - Board Serialization
echo.
echo Starting compilation...
javac -d out -cp src src/**/*.java
if %errorlevel% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)
echo ✓ Compilation successful!
echo.

echo Testing Board implementation...
java -cp out test.BoardTest
echo.

echo Starting GameServer...
echo (Server will open Admin Dashboard window)
start "GameServer" java -cp out server.GameServer
timeout /t 2 /nobreak > nul

echo.
echo Starting first GameClient...
echo (Client will open interactive game window)
start "GameClient 1" java -cp out client.GameClient
timeout /t 2 /nobreak > nul

echo.
echo Starting second GameClient...
start "GameClient 2" java -cp out client.GameClient

echo.
echo ===================================================
echo Demo Instructions:
echo ===================================================
echo 1. In each client window, enter a player name
echo 2. Click "Connect" to join the server
echo 3. When both players connect, game starts automatically
echo 4. Click board cells to make moves (X goes first)
echo 5. Watch real-time board updates in both clients
echo 6. Try the "Restart Game" button after a game ends
echo 7. Observe the Admin Dashboard for session info
echo.
echo Member 4 Features Demonstrated:
echo ✓ Server-side game state management
echo ✓ Board serialization for network transmission  
echo ✓ Live board visualization with interactive UI
echo ✓ Real-time synchronization between players
echo ✓ Win/draw detection with proper game ending
echo ✓ Move highlighting and visual feedback
echo ✓ Game restart functionality
echo.
echo Press any key to exit this demo script...
pause > nul