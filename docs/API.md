# OpenTranslatorLite API 文档

## 入口类

```java
import io.github.wzhy233.open_translator.Translator;
```
## 构造方法

### `Translator()`

使用默认缓存目录创建翻译器

```java
Translator translator = new Translator();
```

### `Translator(String cachePath)`

使用指定缓存目录创建翻译器

```java
Translator translator = new Translator("D:/translator-cache");
```

### `Translator(String cachePath, int threadPoolSize)`

使用指定缓存目录和线程池大小创建翻译器

```java
Translator translator = new Translator("D:/translator-cache", 8);
```

## 翻译接口

### `String translate(String sourceLang, String targetLang, String content)`

同步翻译 适合单条请求

```java
String result = translator.translate("en", "zh", "Hello world");
```

说明

- 支持语言对：`en-zh`、`zh-en`
- `sourceLang` 支持传入 `auto` 自动检测源语言，例如 `translator.translate("auto", "zh", "Hello world")`
- `sourceLang`、`targetLang`、`content` 不能为 `null`
- 同语种输入会直接返回原文
- 空字符串会直接返回空字符串

### `Future<String> translateAsync(String sourceLang, String targetLang, String content)`

异步翻译 适合并发请求

```java
Future<String> future = translator.translateAsync("en", "zh", "Hello");
String result = future.get();
```

### `String[] translateBatch(String sourceLang, String targetLang, String[] contents)`

批量翻译 内部并发执行 返回顺序与输入顺序一致

```java
String[] results = translator.translateBatch("en", "zh", new String[]{
        "Hello",
        "World"
});
```

## 语言对接口

### `String[] getSupportedLanguagePairs()`

返回当前已加载的语言对

```java
String[] pairs = translator.getSupportedLanguagePairs();
```

### `boolean isSupportedPair(String pair)`

检查语言对是否可用

```java
boolean ok = translator.isSupportedPair("en-zh");
```

`auto-*` 也会被视为可用语言对，只要当前模型里存在任意 `*-目标语言` 的真实语言对。

## 缓存接口

### `Translator.CacheStatistics getCacheStatistics()`

返回当前缓存统计信息

```java
Translator.CacheStatistics stats = translator.getCacheStatistics();
System.out.println(stats.totalEntries);
System.out.println(stats.hitRate);
```

字段

- `totalEntries`
- `cacheSizeBytes`
- `hits`
- `misses`
- `hitRate`

### `void clearCache()`

清空当前缓存目录中的翻译结果

```java
translator.clearCache();
```

### `String getCachePath()`

返回当前缓存目录

```java
String path = translator.getCachePath();
```

### `void setCachePath(String newCachePath)`

切换缓存目录 并同步更新配置文件

```java
translator.setCachePath("D:/translator-cache");
```

## 生命周期

### `void shutdown()`

释放线程池和相关资源 应用退出前调用一次即可

```java
translator.shutdown();
```

推荐写法

```java
Translator translator = new Translator();
try {
    String result = translator.translate("en", "zh", "Hello");
    System.out.println(result);
} finally {
    translator.shutdown();
}
```

## 异常抛出


- `IllegalArgumentException` 参数为 `null`
- `IllegalArgumentException` 语言对不支持
- `IllegalStateException` 在 `shutdown()` 之后继续调用
- `IllegalStateException` 翻译器尚未初始化完成
- `RuntimeException` 模型加载失败
- `RuntimeException` 推理失败
- `RuntimeException` 批量翻译中的任务执行失败
