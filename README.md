# DexShell-Java

内存壳学习记录

DexShellS为壳Dex，

ShellTools将iterms下source.apk(源apk)加密整合到classes.dex(壳dex)中，生成新dex(newclasses.dex)，将newclasses.dex替换DexShellS中的classes.dex，修改AndroidManifes中的application和activity入口名，重新打包签名，即可完成内存壳的加固