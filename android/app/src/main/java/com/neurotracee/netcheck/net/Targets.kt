package com.neurotracee.netcheck.net

import com.neurotracee.netcheck.model.Target

/**
 * 测试点位清单，与 Windows 端 netcheck.ps1 的 $Targets 逐条对应。
 * 改这里就等于改两端的默认点位（Windows 端改脚本开头那段即可）。
 */
object Targets {

    val all: List<Target> = listOf(
        Target("百度（对照基准）", "www.baidu.com", "/", "先确认本地宽带本身正常", Target.Group.BASE),
        Target("GitHub API 接口", "api.github.com", "/rate_limit", "Agent 查仓库 / 拉信息", Target.Group.GITHUB),
        Target("GitHub Raw 原始文件", "raw.githubusercontent.com", "/github/gitignore/main/README.md", "Agent 直读文件内容", Target.Group.GITHUB),
        Target("GitHub Git 传输", "github.com", "/git/git.git/info/refs?service=git-upload-pack", "git clone / pull 通道", Target.Group.GITHUB),
        Target("GitHub 仓库打包", "codeload.github.com", "/", "仓库 zip / tar 下载", Target.Group.GITHUB),
        Target("GitHub 主站", "github.com", "/", "网页 / 仓库首页", Target.Group.GITHUB),
        Target("GitHub 静态资源", "github.githubassets.com", "/", "图标 / 页面资源", Target.Group.GITHUB),
        Target("GitHub 头像 CDN", "avatars.githubusercontent.com", "/", "头像 / 图片", Target.Group.GITHUB),
        Target("GitHub Gist", "gist.githubusercontent.com", "/", "代码片段 / 配置", Target.Group.GITHUB),
        Target("GitHub Releases", "objects.githubusercontent.com", "/", "发行包下载", Target.Group.GITHUB),
        Target("GitHub Pages", "github.io", "/", "Pages 站点", Target.Group.GITHUB),
        Target("Hugging Face", "huggingface.co", "/", "模型 / 数据集", Target.Group.AI),
        Target("Docker 镜像仓库", "registry-1.docker.io", "/v2/", "docker pull", Target.Group.CONTAINER),
        Target("Docker Hub 官网", "hub.docker.com", "/", "镜像页 / 登录", Target.Group.CONTAINER)
    )

    /** 快捷模式：只留基准站与 GitHub 系，和桌面端 -Quick 的筛选条件一致。 */
    fun quickSubset(): List<Target> =
        all.filter { it.host.contains("github") || it.host == "www.baidu.com" }
}
