# OpenTranslatorLite

## 简介

OpenTranslatorLite 是一款基于 Java + CTranslate2 worker 的轻量级离线翻译项目

### 特性

- 支持同步、异步、批量翻译
- 本地翻译缓存
- 热路径内存缓存，可应用于高重复请求环境

**本项目使用 *MIT* 开源协议，详情见 [LICENSE](LICENSE.txt)**

## 环境要求

- JDK 11+
- Maven 3.8+

## 安装


如果你是第一次部署本仓库，有两种方式：

1. 自动方式

直接创建 `Translator`

2. 手动方式

先安装 Python 依赖并下载 CTranslate2 模型：

```bash
pip install -r scripts/requirements.txt
python scripts/init_models.py
```

模型默认下载到：

```text
~/.open_translator/OpenTranslatorLite/models/ctranslate2
```

运行时说明：

- Java 侧会启动一个本地 Python worker，并通过 CTranslate2 执行翻译。
- jar 不再内嵌模型文件，所以最终包体会远小于旧版 ONNX fat jar。
- 如需自定义 Python 或模型目录，可分别设置 `open_translator.python` 和 `open_translator.model_root` 系统属性。
- 内置设置向导支持 Markdown 文档渲染、链接跳转、许可证签署、依赖安装和模型下载。
- 许可证签署按设备校验，新设备第一次使用必须重新 setup 并签署。
- 许可证签署信息会加密保存，并带完整性校验，避免被直接篡改。
- 设置向导支持浅色/深色模式和卡片切换动画。


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

## 用户文档

最终用户使用说明见 [docs/USER_GUIDE.md](docs/USER_GUIDE.md)

## 开发者文档

开发与集成说明见 [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md)

## 测试文档

真实模型测试与测试前置说明见 [docs/TEST_SETUP.md](docs/TEST_SETUP.md)

