@echo off
REM YaicaBuilder — запуск графического сборщика TikYaicaTok
REM Двойной клик по этому файлу откроет GUI сборщика.

cd /d "%~dp0"

REM пытаемся найти python
where python >nul 2>nul
if %errorlevel%==0 (
    python YaicaBuilder.py
    goto :end
)
where py >nul 2>nul
if %errorlevel%==0 (
    py YaicaBuilder.py
    goto :end
)

echo [YaicaBuilder] Python не найден.
echo Установи Python 3 с https://python.org и перезапусти.
pause

:end
if %errorlevel% neq 0 pause
