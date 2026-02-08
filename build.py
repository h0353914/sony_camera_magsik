#!/usr/bin/env python3
"""
Sony Camera Magisk Module Builder
自動建置 Sony Camera 的 Magisk 模組
"""

import os
import sys
import subprocess
from pathlib import Path

# 顏色輸出
class Colors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'

def print_header(text):
    """印出標題"""
    print(f"{Colors.HEADER}{Colors.BOLD}{'='*60}{Colors.ENDC}")
    print(f"{Colors.HEADER}{Colors.BOLD}{text:^60}{Colors.ENDC}")
    print(f"{Colors.HEADER}{Colors.BOLD}{'='*60}{Colors.ENDC}")

def print_info(text):
    """印出資訊"""
    print(f"{Colors.OKBLUE}[INFO]{Colors.ENDC} {text}")

def print_success(text):
    """印出成功訊息"""
    print(f"{Colors.OKGREEN}[SUCCESS]{Colors.ENDC} {text}")

def print_error(text):
    """印出錯誤訊息"""
    print(f"{Colors.FAIL}[ERROR]{Colors.ENDC} {text}")

def print_warning(text):
    """印出警告訊息"""
    print(f"{Colors.WARNING}[WARNING]{Colors.ENDC} {text}")

def check_gradle():
    """檢查 Gradle 是否已安裝"""
    try:
        result = subprocess.run(
            ['gradle', '--version'],
            capture_output=True,
            text=True,
            check=False
        )
        if result.returncode == 0:
            print_success("Gradle 已安裝")
            return True
        else:
            print_error("Gradle 未安裝或無法執行")
            return False
    except FileNotFoundError:
        print_error("找不到 Gradle，請先安裝 Gradle")
        print_info("安裝方式: https://gradle.org/install/")
        return False

def check_out_directory():
    """檢查 out 目錄是否存在"""
    script_dir = Path(__file__).resolve().parent
    out_dir = script_dir.parent / "out"
    
    if not out_dir.exists():
        print_warning(f"out 目錄不存在: {out_dir}")
        print_info("請先編譯相機應用程式")
        return False
    
    # 檢查是否有檔案
    files = list(out_dir.rglob("*"))
    if len(files) == 0:
        print_warning("out 目錄是空的")
        print_info("請先編譯相機應用程式")
        return False
    
    print_success(f"找到 out 目錄，包含 {len(files)} 個檔案/目錄")
    return True

def build_magisk_module(clean=False):
    """建置 Magisk 模組"""
    script_dir = Path(__file__).resolve().parent
    
    print_info(f"工作目錄: {script_dir}")
    
    # 切換到腳本目錄
    os.chdir(script_dir)
    
    # 如果需要清理
    if clean:
        print_info("正在清理之前的建置...")
        result = subprocess.run(
            ['gradle', 'clean'],
            capture_output=False,
            text=True
        )
        if result.returncode != 0:
            print_error("清理失敗")
            return False
        print_success("清理完成")
    
    # 執行建置
    print_info("正在建置 Magisk 模組...")
    result = subprocess.run(
        ['gradle', 'build', '--console=plain'],
        capture_output=False,
        text=True
    )
    
    if result.returncode == 0:
        print_success("建置成功！")
        
        # 顯示輸出檔案位置
        output_file = script_dir / "build" / "sony_camera_magisk.zip"
        if output_file.exists():
            file_size = output_file.stat().st_size / 1024 / 1024  # MB
            print_success(f"模組檔案: {output_file}")
            print_success(f"檔案大小: {file_size:.2f} MB")
        
        return True
    else:
        print_error("建置失敗")
        return False

def main():
    """主程式"""
    print_header("Sony Camera Magisk Module Builder")
    
    # 解析參數
    clean = '--clean' in sys.argv or '-c' in sys.argv
    
    # 檢查 Gradle
    if not check_gradle():
        sys.exit(1)
    
    # 檢查 out 目錄
    if not check_out_directory():
        print_warning("繼續建置，但模組可能不完整")
    
    # 建置
    success = build_magisk_module(clean=clean)
    
    if success:
        print_header("建置完成")
        print_info("請將 build/sony_camera_magisk.zip 刷入裝置")
        sys.exit(0)
    else:
        print_header("建置失敗")
        sys.exit(1)

if __name__ == "__main__":
    main()
