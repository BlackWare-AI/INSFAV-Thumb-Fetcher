#!/usr/bin/env bash
set -euo pipefail
insfav_source=${1:?Pass a current INSFAV checkout path}
fetcher_source=$(cd "$(dirname "$0")/.." && pwd)
check_dir=$(mktemp -d)
trap 'python3 -c "import shutil,sys;shutil.rmtree(sys.argv[1])" "$check_dir"' EXIT
cat > "$check_dir/FilenameBridge.java" <<'JAVA'
package com.insfav.thumbfetcher;
public final class FilenameBridge { public static String filename(String id) { return Identity.outputFilename(id); } }
JAVA
cat > "$check_dir/CompatibilityCheck.java" <<'JAVA'
package com.instagram.insfav;
public final class CompatibilityCheck {
 public static void main(String[] args) {
  for(int i=0;i<10000;i++) {
   String pageId=" @ACCOUNT_"+i+".I ";
   String file=com.insfav.thumbfetcher.FilenameBridge.filename(pageId);
   if(!ThumbnailPolicy.normalize(pageId).equals(ThumbnailPolicy.username(file)))throw new AssertionError("Mismatch "+i);
  }
  System.out.println("PASS: 10000 username filenames match current INSFAV ThumbnailPolicy");
 }
}
JAVA
javac -d "$check_dir" "$fetcher_source/app/src/main/java/com/insfav/thumbfetcher/Identity.java" "$insfav_source/app/src/main/java/com/instagram/insfav/ThumbnailPolicy.java" "$check_dir/FilenameBridge.java" "$check_dir/CompatibilityCheck.java"
java -cp "$check_dir" com.instagram.insfav.CompatibilityCheck
