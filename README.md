# OpenTranslatorLite

## 简介

OpenTranslatorLite是一款基于 ONNXRuntime + Marian 的轻量级开源神经网络翻译项目

### 特性

- 支持同步、异步、批量翻译
- 本地翻译缓存
- 热路径内存缓存，可应用于高重复请求环境

**本项目使用 *MIT* 开源协议，详情见 [LICENSE](LICENSE.txt)**

## 环境要求

- JDK 11+
- Maven 3.8+

## 安装


如果你是第一次部署本仓库，请运行项目目录下 ```scripts/init_models.py``` 以配置模型


```xml
<dependency>
    <groupId>io.github.wzhy233</groupId>
    <artifactId>open-translator-lite</artifactId>
    <version>1.0.0</version>
</dependency>
```

如果你是直接打开当前仓库

```bash
mvn test
mvn package
```

## 快速开始

```java
import io.github.wzhy233.open_translator.Translator;

public class Demo {
    public static void main(String[] args) throws Exception {
        Translator translator = new Translator();
        try {
            String text = translator.translate("en", "zh", "Hello world");
            System.out.println(text);

            String[] batch = translator.translateBatch("en", "zh", new String[]{
                    "Hello",
                    "World"
            });
            System.out.println(String.join(" | ", batch));
        } finally {
            translator.shutdown();
        }
    }
}
```

## 缓存

默认缓存目录

```text
~/.open_translator/OpenTranslatorLite/cache
```

你可以使用下面两种方式切换缓存目录

```java
Translator translator = new Translator("D:/translator-cache");
```

```java
translator.setCachePath("D:/translator-cache");
```

缓存相关接口

- `translator.getCachePath()`
- `translator.clearCache()`
- `translator.getCacheStatistics()`

## API 文档

详细接口说明见 [docs/API.md](docs/API.md)

------------------

