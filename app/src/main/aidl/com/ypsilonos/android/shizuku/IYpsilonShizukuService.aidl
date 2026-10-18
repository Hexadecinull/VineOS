package com.ypsilonos.android.shizuku;

interface IYpsilonShizukuService {
    void destroy() = 16777114; // reserved id, Shizuku's own destroy hook
    void exit() = 1;
    String probeNamespaces() = 2;
}
