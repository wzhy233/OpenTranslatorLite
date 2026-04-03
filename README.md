# OpenTranslatorLite

## 简介

OpenTranslatorLite 是一款基于 Java + CTranslate2 worker 的轻量级离线翻译项目

### 特性

- 支持同步、异步、批量翻译
- 本地翻译缓存
- 热路径内存缓存，可应用于高重复请求环境

**本项目使用 *MIT* 开源协议，详情见 [LICENSE](LICENSE.txt)**

## 环境要求

- JDK 8+
- Maven 3.8+

## 安装


### 如果你是第一次部署本项目：

在项目 ``pom.xml`` 文件中添加依赖

```xml
<dependency>
    <groupId>io.github.wzhy233</groupId>
    <artifactId>open-translator-lite</artifactId>
    <version>1.0.0</version>
</dependency>
```

在类文件中添加导入

```java
import io.github.wzhy233.open_translator.Translator;
```

创建 `Translator` 实例

```java
Translator translator = new Translator();
```

根据向导完成项目运行时准备

*模型默认下载到：

```text
~/.open_translator/OpenTranslatorLite/models/ctranslate2
```

### 如果你是直接打开当前仓库

运行 ``scripts/init_models.py``

```bash
python scripts/init_models.py
```
打包与测试

```bash
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

## 用户文档

最终用户使用说明见 [docs/USER_GUIDE.md](docs/USER_GUIDE.md)

## 开发者文档

开发与集成说明见 [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md)

## 测试文档

真实模型测试与测试前置说明见 [docs/TEST_SETUP.md](docs/TEST_SETUP.md)

