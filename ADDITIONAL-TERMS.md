# 署名与出处要求 · Attribution & Notice Terms

本文件说明「GitHub 网络自检 / NetCheck」在 **Apache License 2.0** 之下，
二次开发、分发、商用时必须做到的事情。它与 [LICENSE](LICENSE)、[NOTICE.md](NOTICE.md)
一起构成完整的授权条件。

> **和 PDF 隐私打码工具那份不一样的地方，先说清楚。**
> 那个项目用的是 AGPL-3.0，附加条款挂在协议**第 7 条 (b) 项**下面，并且有 copyleft（改了必须照样开源）。
> 本项目换成 **Apache-2.0**：**没有强制的开源传染性**，你可以改完闭源卖钱；
> 但署名、NOTICE、修改标记这三件事仍然是协议的硬要求，写在**第 4 条**与**第 6 条**里，不是作者额外加的。
> 所以下面没有任何「超出协议」的自造条款——每一条都能落回协议原文。

凡复制、分发、修改、打包本项目（含编译后的 APK），即视为接受下列条件。
如果你不接受，请不要使用本项目的代码。

---

## 一、必须保留的三样东西

**1. 作者署名**
下面两句原文保留，不得删除、不得改写、不得替换成其他人的名字：

```
指导作者：项目创作时为初中生
由 豆包工作任务的 Agent × DeepSeek-V4.1-Flash 开发
```

依据：Apache-2.0 第 4 条 (c) 项 —— 分发衍生作品时，必须保留源码中所有版权、专利、商标与署名声明。

**2. 原项目地址**
必须让程序的使用者**看得见**这个地址：

```
https://github.com/neurotracee/github-netcheck
https://gitee.com/bio234/github-netcheck      （国内镜像，两处写任一处或都写均可）
```

依据：第 4 条 (d) 项 —— 作品包含 NOTICE 文件时，衍生作品必须附带其中署名声明的可读副本，
放在 NOTICE 文件、源码/文档、或衍生产品生成的展示位置（本项目选的是「程序界面 + 报告页脚」）。

（具体放在哪里算数，见第二节。地址写了就行，不要求做成链接。）

**3. 修改说明**
依据：第 4 条 (b) 项 —— 修改过的文件必须带上显著的「已修改」声明。

如果你改过代码、改过界面、改过功能，必须在程序里**显著注明**：

> 本程序基于 https://github.com/neurotracee/github-netcheck
> （镜像：https://gitee.com/bio234/github-netcheck）修改，非原作者发布。

这一条是为了避免别人误以为改动后的版本是原作者做的——尤其是当你的版本有 bug、
有隐私风险、或者开始收费的时候。

---

## 二、地址放在哪里算数

以下**任何一处**做到就算合规，不用全都做：

| 位置 | 说明 |
| --- | --- |
| 程序的「关于」窗口 / 设置页 | 最省事。原版已经有这几行，别删就行。 |
| 首页 / 启动页的页脚 | 一行小字也可以。安卓端现在就是这么做的。 |
| 生成的报告页脚 | 本项目的 HTML 报告底部有两行地址。 |
| 菜单栏「帮助 → 关于」 | 桌面软件通用做法。 |
| 脚本首屏输出 / `-About` 输出 | 如果你的版本只有命令行。 |
| 随附文档的第一屏 | 仅限没有图形界面的情况；有界面就必须放界面里。 |

**只有一处不算数**：把地址埋在 `LICENSE` 文件或几百行之外的源码注释里，
使用者正常操作时根本看不到——这不算「让使用者看得见」。

---

## 三、商业使用

**可以商用，也可以闭源。** Apache-2.0 不是 copyleft：它不要求你把修改后的代码公开，
也不要求你以同样协议授权你的衍生作品（第 5 条只约束「向本项目提交贡献」这一种情形）。

商用需要做到：

- 上面第一节的三条（署名 + 地址 + 修改说明）；
- 分发时附带 `LICENSE` 与 `NOTICE.md`（第 4 条 (a)(d)）；
- 不要暗示原作者为你的版本背书（第 6 条：不得使用作者的商号、商标、产品名做背书）。

**不需要**做的事：

- ❌ 不需要付钱给作者
- ❌ 不需要发邮件、不需要等作者批准、不需要在 issue 里报备
- ❌ 不需要公开你修改后的源码（这是与 AGPL 项目最大的区别）
- ❌ 不需要保留作者的界面、功能、bug——改得面目全非也行，只要署名和地址还在

---

## 四、作者的态度（说实话）

写这条不是为了找谁的麻烦，就是为了**留个名**。

作者平时没有充足的时间和精力，大约一两个月才上来看一两次，也不会去追着谁打官司——没那个功夫。

所以这些要求的实际意义是：

- 给**守规矩的人**一个明确依据：照着做，你不用问任何人，也不用担心哪天被找上门；
- 给**想抹掉来源的人**一个明确态度：这件事是明确不被允许的，不是「没人管所以随便」。

法律上真正兜底的是 Apache-2.0 本身的条款与著作权法。如果你认为某处写法超出了协议范围，
以 `LICENSE` 原文为准。

---

## 五、English Summary

This project is licensed under the **Apache License 2.0**. Attribution requirements follow
**Section 4** of that license (they are not extra terms invented by the author):

1. **Preserve attribution** (§4(c)): keep
   `Advisor: was a junior high school student when this project was created` and
   `Developed by Doubao Work Agent × DeepSeek-V4.1-Flash`.
2. **Display the original project URL** (§4(d), because this repository ships a NOTICE file):
   `https://github.com/neurotracee/github-netcheck` (mirror: `https://gitee.com/bio234/github-netcheck`),
   somewhere the end user can actually see it — an About screen, a footer, a report footer,
   or command-line output.
3. **Mark modified files** (§4(b)): state clearly that the version was modified and must not
   imply endorsement by the original author.
4. **Ship the license and notice** (§4(a)(d)): distribute `LICENSE` and `NOTICE.md` with any
   binary or script you redistribute.
5. **No endorsement, no trademark use** (§6): the names "GitHub 网络自检" / "NetCheck" and the
   application icon are **not** licensed. Rename your fork.
6. **Commercial use and closed-source redistribution are permitted.** Apache-2.0 has no
   copyleft obligation — unlike the author's other project (PDF Privacy Masker, AGPL-3.0),
   you do not have to publish your modified source.

Where this file is ambiguous, the Apache-2.0 text prevails.
