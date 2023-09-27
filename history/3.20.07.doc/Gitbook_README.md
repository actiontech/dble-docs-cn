# 如何更新文档

1. 更新文档
2. 执行 `make gitbook_preview` 可预览修改
3. 在master分支提交修改
4. `git status` 确认没有未提交文件
5. 执行`make`, 将文档编译成静态文件, 部署到`gh-pages`, 并push到github上