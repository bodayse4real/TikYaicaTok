#!/usr/bin/env python3
"""
YaicaBuilder — графический сборщик TikYaicaTok.

Запуск:
    python YaicaBuilder.py
"""

from __future__ import annotations

import io
import os
import queue
import subprocess
import sys
import threading
import tkinter as tk
import tkinter.filedialog as fd
import tkinter.font as tkfont
from tkinter import ttk

ROOT = os.path.dirname(os.path.abspath(__file__))

# ── цвета (TikTok-тёмная тема) ──────────────────────────────────────────────

BG        = "#1a1a1a"
BG2       = "#2a2a2a"
BG3       = "#333333"
PINK      = "#FE2C55"
PINK_DIM  = "#c41f3f"
CYAN      = "#25F4EE"
FG        = "#ffffff"
FG_DIM    = "#aaaaaa"
SUCCESS   = "#4caf50"
ERROR     = "#f44336"
WARN      = "#ff9800"
LOG_BG    = "#111111"

# ── маленькие утилиты ────────────────────────────────────────────────────────

def _btn(parent, text, cmd, color=PINK, fg=FG, padx=16, pady=8, **kw):
    b = tk.Button(parent, text=text, command=cmd,
                  bg=color, fg=fg, activebackground=PINK_DIM, activeforeground=FG,
                  relief=tk.FLAT, bd=0, cursor="hand2",
                  padx=padx, pady=pady, **kw)
    b.bind("<Enter>", lambda e: b.config(bg=PINK_DIM if color == PINK else BG3))
    b.bind("<Leave>", lambda e: b.config(bg=color))
    return b

def _label(parent, text, fg=FG_DIM, **kw):
    return tk.Label(parent, text=text, bg=BG, fg=fg, **kw)

def _entry(parent, textvariable, **kw):
    return tk.Entry(parent, textvariable=textvariable,
                    bg=BG2, fg=FG, insertbackground=FG,
                    relief=tk.FLAT, bd=0, highlightthickness=1,
                    highlightbackground=BG3, highlightcolor=PINK, **kw)

# ── главное окно ─────────────────────────────────────────────────────────────

class YaicaBuilder(tk.Tk):

    def __init__(self):
        super().__init__()

        self.title("YaicaBuilder — TikYaicaTok")
        self.configure(bg=BG)
        self.resizable(True, True)
        self.minsize(640, 520)

        # состояние
        self._apk_var    = tk.StringVar()
        self._out_var    = tk.StringVar(value=os.path.join(ROOT, "build", "TikYaicaTok.apk"))
        self._accent_var = tk.StringVar()
        self._ks_var     = tk.StringVar()
        self._ks_alias   = tk.StringVar(value="androiddebugkey")
        self._ks_pass    = tk.StringVar(value="android")
        self._key_pass   = tk.StringVar(value="android")
        self._log_q: queue.Queue[str | None] = queue.Queue()
        self._running    = False
        self._show_ks    = tk.BooleanVar(value=False)

        self._build_ui()
        self._poll_log()

        # если tiktok.apk уже лежит рядом — подставить
        default_apk = os.path.join(ROOT, "tiktok.apk")
        if os.path.exists(default_apk):
            self._apk_var.set(default_apk)

    # ── UI ───────────────────────────────────────────────────────────────────

    def _build_ui(self):
        mono = tkfont.Font(family="Consolas", size=9)

        # ── шапка ────────────────────────────────────────────────────────────
        header = tk.Frame(self, bg=BG, pady=12)
        header.pack(fill=tk.X, padx=20)

        tk.Label(header, text="Yaica", bg=BG, fg=PINK,
                 font=tkfont.Font(family="Arial", size=22, weight="bold")).pack(side=tk.LEFT)
        tk.Label(header, text="Builder", bg=BG, fg=FG,
                 font=tkfont.Font(family="Arial", size=22, weight="bold")).pack(side=tk.LEFT)
        tk.Label(header, text="  2.1.0  |  TikYaicaTok mod assembler",
                 bg=BG, fg=FG_DIM,
                 font=tkfont.Font(family="Arial", size=10)).pack(side=tk.LEFT, pady=4)

        sep = tk.Frame(self, bg=BG3, height=1)
        sep.pack(fill=tk.X)

        # ── форма ────────────────────────────────────────────────────────────
        form = tk.Frame(self, bg=BG, padx=20, pady=12)
        form.pack(fill=tk.X)
        form.columnconfigure(1, weight=1)

        self._row_file(form, 0, "TikTok APK",
                       "Оригинальный APK TikTok (arm64-v8a, nodpi, тип APK не BUNDLE)",
                       self._apk_var, self._pick_apk)
        self._row_file(form, 1, "Выходной APK",
                       "Куда сохранить готовый мод",
                       self._out_var, self._pick_out)

        # цвет акцента
        self._row_plain(form, 2, "Акцент (HEX)",
                        "Цвет вместо TikTok-розового, например 8DD1B0 (пусто = оставить)",
                        self._accent_var, "например: 8DD1B0")

        # опция: кастомный кейстор
        ks_header = tk.Frame(form, bg=BG)
        ks_header.grid(row=3, column=0, columnspan=3, sticky="w", pady=(10, 2))
        tk.Checkbutton(ks_header, text="Подписать своим ключом (keystore)",
                       variable=self._show_ks, command=self._toggle_ks,
                       bg=BG, fg=FG_DIM, selectcolor=BG2, activebackground=BG,
                       activeforeground=FG, relief=tk.FLAT, cursor="hand2").pack(side=tk.LEFT)

        self._ks_frame = tk.Frame(form, bg=BG)
        self._ks_frame.grid(row=4, column=0, columnspan=3, sticky="ew")
        self._ks_frame.columnconfigure(1, weight=1)
        self._row_file(self._ks_frame, 0, "Keystore файл", "", self._ks_var, self._pick_ks)
        self._row_plain(self._ks_frame, 1, "Alias",       "", self._ks_alias)
        self._row_plain(self._ks_frame, 2, "Store пароль","", self._ks_pass, show="*")
        self._row_plain(self._ks_frame, 3, "Key пароль",  "", self._key_pass, show="*")
        self._ks_frame.grid_remove()

        # ── кнопки ────────────────────────────────────────────────────────────
        btns = tk.Frame(self, bg=BG, padx=20, pady=8)
        btns.pack(fill=tk.X)

        self._build_btn = _btn(btns, "▶  СОБРАТЬ МОД", self._start_build,
                               font=tkfont.Font(family="Arial", size=11, weight="bold"))
        self._build_btn.pack(side=tk.LEFT)

        self._stop_btn = _btn(btns, "■  Стоп", self._stop_build,
                              color=BG3, state=tk.DISABLED)
        self._stop_btn.pack(side=tk.LEFT, padx=(8, 0))

        _btn(btns, "🗑  Очистить лог", self._clear_log, color=BG3).pack(side=tk.LEFT, padx=(8, 0))

        self._open_btn = _btn(btns, "📂  Открыть папку build", self._open_build_dir, color=BG3)
        self._open_btn.pack(side=tk.RIGHT)

        # ── статус ────────────────────────────────────────────────────────────
        self._status_var = tk.StringVar(value="Готов к сборке")
        status_bar = tk.Frame(self, bg=BG2, pady=4)
        status_bar.pack(fill=tk.X, padx=20)
        self._status_lbl = tk.Label(status_bar, textvariable=self._status_var,
                                    bg=BG2, fg=FG_DIM,
                                    font=tkfont.Font(family="Arial", size=9),
                                    anchor="w")
        self._status_lbl.pack(fill=tk.X, padx=8)

        # ── прогресс-бар ──────────────────────────────────────────────────────
        style = ttk.Style(self)
        style.theme_use("clam")
        style.configure("Pink.Horizontal.TProgressbar",
                         troughcolor=BG2, background=PINK, thickness=4)
        self._pbar = ttk.Progressbar(self, style="Pink.Horizontal.TProgressbar",
                                     mode="indeterminate")
        self._pbar.pack(fill=tk.X, padx=20, pady=(0, 4))

        # ── лог ───────────────────────────────────────────────────────────────
        log_frame = tk.Frame(self, bg=BG, padx=20)
        log_frame.pack(fill=tk.BOTH, expand=True, pady=(0, 12))

        log_label = tk.Frame(log_frame, bg=BG)
        log_label.pack(fill=tk.X)
        tk.Label(log_label, text="Лог сборки", bg=BG, fg=FG_DIM,
                 font=tkfont.Font(family="Arial", size=9, weight="bold")).pack(side=tk.LEFT)
        self._lines_var = tk.StringVar(value="")
        tk.Label(log_label, textvariable=self._lines_var, bg=BG, fg=BG3,
                 font=tkfont.Font(family="Arial", size=9)).pack(side=tk.RIGHT)

        log_wrap = tk.Frame(log_frame, bg=BG3, pady=1, padx=1)
        log_wrap.pack(fill=tk.BOTH, expand=True)

        self._log = tk.Text(log_wrap, bg=LOG_BG, fg=FG, font=mono,
                            relief=tk.FLAT, bd=0, state=tk.DISABLED,
                            wrap=tk.NONE, selectbackground=BG3)
        scroll_y = tk.Scrollbar(log_wrap, command=self._log.yview, bg=BG2,
                                 troughcolor=LOG_BG, width=10)
        scroll_x = tk.Scrollbar(log_wrap, command=self._log.xview, bg=BG2,
                                 troughcolor=LOG_BG, width=10, orient=tk.HORIZONTAL)
        self._log.configure(yscrollcommand=scroll_y.set, xscrollcommand=scroll_x.set)

        scroll_y.pack(side=tk.RIGHT, fill=tk.Y)
        scroll_x.pack(side=tk.BOTTOM, fill=tk.X)
        self._log.pack(fill=tk.BOTH, expand=True)

        # теги для раскраски
        self._log.tag_config("ok",    foreground=SUCCESS)
        self._log.tag_config("err",   foreground=ERROR)
        self._log.tag_config("warn",  foreground=WARN)
        self._log.tag_config("info",  foreground=CYAN)
        self._log.tag_config("head",  foreground=PINK,
                             font=tkfont.Font(family="Consolas", size=9, weight="bold"))
        self._log.tag_config("dim",   foreground=FG_DIM)

        self._log_lines = 0

    def _row_file(self, parent, row, label, hint, var, cmd):
        tk.Label(parent, text=label, bg=BG, fg=FG,
                 width=14, anchor="w").grid(row=row, column=0, sticky="w", pady=4)
        e = _entry(parent, var)
        e.grid(row=row, column=1, sticky="ew", padx=(6, 6))
        if hint:
            e.bind("<FocusIn>",  lambda ev, w=e, h=hint: self._hint(w, h, True))
            e.bind("<FocusOut>", lambda ev, w=e, h=hint: self._hint(w, h, False))
        _btn(parent, "…", cmd, color=BG3, pady=4, padx=10).grid(row=row, column=2)

    def _row_plain(self, parent, row, label, hint, var, placeholder="", show=None):
        tk.Label(parent, text=label, bg=BG, fg=FG,
                 width=14, anchor="w").grid(row=row, column=0, sticky="w", pady=4)
        kw = {}
        if show:
            kw["show"] = show
        e = _entry(parent, var, **kw)
        e.grid(row=row, column=1, sticky="ew", padx=(6, 6), columnspan=2)
        if hint:
            e.bind("<FocusIn>",  lambda ev, w=e, h=hint: self._hint(w, h, True))
            e.bind("<FocusOut>", lambda ev, w=e, h=hint: self._hint(w, h, False))

    def _hint(self, widget, text, entering):
        # показать подсказку в статус-баре
        if entering:
            self._status_var.set(text)
            self._status_lbl.config(fg=FG_DIM)

    def _toggle_ks(self):
        if self._show_ks.get():
            self._ks_frame.grid()
        else:
            self._ks_frame.grid_remove()

    # ── диалоги выбора файлов ─────────────────────────────────────────────────

    def _pick_apk(self):
        path = fd.askopenfilename(
            title="Выбери оригинальный TikTok APK",
            initialdir=ROOT,
            filetypes=[("APK файлы", "*.apk"), ("Все файлы", "*.*")])
        if path:
            self._apk_var.set(path)

    def _pick_out(self):
        path = fd.asksaveasfilename(
            title="Куда сохранить готовый APK",
            initialdir=os.path.join(ROOT, "build"),
            initialfile="TikYaicaTok.apk",
            defaultextension=".apk",
            filetypes=[("APK файлы", "*.apk")])
        if path:
            self._out_var.set(path)

    def _pick_ks(self):
        path = fd.askopenfilename(
            title="Выбери keystore",
            filetypes=[("Keystore", "*.jks *.keystore"), ("Все файлы", "*.*")])
        if path:
            self._ks_var.set(path)

    # ── сборка ────────────────────────────────────────────────────────────────

    def _start_build(self):
        apk = self._apk_var.get().strip()
        if not apk:
            self._status("Сначала выбери TikTok APK", "err")
            return
        if not os.path.exists(apk):
            self._status(f"Файл не найден: {apk}", "err")
            return

        out = self._out_var.get().strip()
        if not out:
            self._status("Укажи путь для готового APK", "err")
            return

        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)

        cmd = [sys.executable, "-m", "margyt", apk, "-o", out]

        accent = self._accent_var.get().strip().lstrip("#")
        if accent:
            cmd += ["--accent", accent]

        if self._show_ks.get() and self._ks_var.get().strip():
            cmd += ["--keystore",          self._ks_var.get().strip(),
                    "--keystore-alias",    self._ks_alias.get().strip(),
                    "--keystore-password", self._ks_pass.get(),
                    "--key-password",      self._key_pass.get()]

        self._clear_log()
        self._set_running(True)
        self._status("Идёт сборка…", "dim")
        self._log_write("==> YaicaBuilder запускает сборку\n", "head")
        self._log_write(f"    команда: {' '.join(cmd)}\n\n", "dim")

        threading.Thread(target=self._run_build, args=(cmd,), daemon=True).start()

    def _run_build(self, cmd: list[str]):
        try:
            proc = subprocess.Popen(
                cmd,
                cwd=ROOT,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                bufsize=1,
            )
            self._proc = proc
            for line in proc.stdout:
                self._log_q.put(line)
            proc.wait()
            self._log_q.put(None)  # сигнал конца
            self._log_q.put(("__done__", proc.returncode))
        except Exception as e:
            self._log_q.put(f"\nОшибка запуска: {e}\n")
            self._log_q.put(None)
            self._log_q.put(("__done__", 1))

    def _stop_build(self):
        proc = getattr(self, "_proc", None)
        if proc and proc.poll() is None:
            proc.terminate()
            self._log_write("\n[Остановлено пользователем]\n", "warn")
            self._status("Остановлено", "warn")
        self._set_running(False)

    def _set_running(self, running: bool):
        self._running = running
        state_build = tk.DISABLED if running else tk.NORMAL
        state_stop  = tk.NORMAL   if running else tk.DISABLED
        self._build_btn.config(state=state_build)
        self._stop_btn.config(state=state_stop)
        if running:
            self._pbar.start(12)
        else:
            self._pbar.stop()
            self._pbar["value"] = 0

    # ── polling лога из очереди ───────────────────────────────────────────────

    def _poll_log(self):
        try:
            while True:
                item = self._log_q.get_nowait()
                if item is None:
                    pass
                elif isinstance(item, tuple) and item[0] == "__done__":
                    code = item[1]
                    self._set_running(False)
                    if code == 0:
                        out = self._out_var.get().strip()
                        self._log_write("\n✓ Готово! APK сохранён:\n", "ok")
                        self._log_write(f"  {out}\n", "ok")
                        self._status(f"✓ Успешно → {os.path.basename(out)}", "ok")
                    else:
                        self._log_write(f"\n✗ Сборка завершилась с ошибкой (код {code})\n", "err")
                        self._status("✗ Ошибка сборки — смотри лог", "err")
                else:
                    self._log_append(str(item))
        except queue.Empty:
            pass
        self.after(80, self._poll_log)

    def _log_append(self, line: str):
        """Пишет строку в лог с раскраской по ключевым словам."""
        low = line.lower()
        if any(k in low for k in ("error", "failed", "exception", "traceback", "✗")):
            tag = "err"
        elif any(k in low for k in ("warn", "warning", "skip")):
            tag = "warn"
        elif any(k in low for k in ("==>", "done", "writing", "signing", "✓")):
            tag = "head"
        elif any(k in low for k in ("download", "fetch", "ok", "found")):
            tag = "info"
        else:
            tag = None
        self._log_write(line, tag)

    def _log_write(self, text: str, tag: str | None = None):
        self._log.config(state=tk.NORMAL)
        if tag:
            self._log.insert(tk.END, text, tag)
        else:
            self._log.insert(tk.END, text)
        self._log.config(state=tk.DISABLED)
        self._log.see(tk.END)
        self._log_lines += text.count("\n")
        self._lines_var.set(f"{self._log_lines} строк")

    def _clear_log(self):
        self._log.config(state=tk.NORMAL)
        self._log.delete("1.0", tk.END)
        self._log.config(state=tk.DISABLED)
        self._log_lines = 0
        self._lines_var.set("")

    # ── статус ────────────────────────────────────────────────────────────────

    def _status(self, msg: str, kind: str = "dim"):
        colors = {"ok": SUCCESS, "err": ERROR, "warn": WARN,
                  "head": PINK, "info": CYAN, "dim": FG_DIM}
        self._status_var.set(msg)
        self._status_lbl.config(fg=colors.get(kind, FG_DIM))

    # ── открыть папку build ───────────────────────────────────────────────────

    def _open_build_dir(self):
        build_dir = os.path.join(ROOT, "build")
        os.makedirs(build_dir, exist_ok=True)
        if sys.platform == "win32":
            os.startfile(build_dir)
        elif sys.platform == "darwin":
            subprocess.Popen(["open", build_dir])
        else:
            subprocess.Popen(["xdg-open", build_dir])


# ── точка входа ──────────────────────────────────────────────────────────────

if __name__ == "__main__":
    app = YaicaBuilder()
    app.mainloop()
