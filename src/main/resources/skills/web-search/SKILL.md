---
name: web-search
description: Use this skill when the user needs recent public internet information such as official announcements, release notes, news, vulnerability notices, or public documentation.
---

# Web Search Skill

## Purpose

Use this skill when the user needs recent or public information from the internet, such as official announcements, release notes, documentation updates, news, vulnerability notices, or public best practices.

## When To Use

- The user asks for the latest, current, recent, today, this week, or official public information.
- The user asks for product release notes, version updates, official documentation, news, public incidents, or vendor announcements.
- Internal documentation is not enough and the answer depends on public information outside the private knowledge base.

## Tool To Call

- Read this skill first, then call `searchWeb`.

## Response Rules

- Summarize the search findings instead of copying long passages.
- Always keep and cite the source links in the answer.
- Prefer official websites, vendor documentation, project release pages, or high-quality public references.
- If no reliable result is found, state that clearly and do not fabricate external facts.
- Do not treat public search results as internal company facts.

## Limits

- This skill is for public internet information, not for private internal procedures.
- For internal SOP, platform operations, or company-specific knowledge, prefer the internal docs tool.

## Examples

- "Spring AI 最新版本是什么？"
- "帮我查一下 Kubernetes 官方文档关于 liveness probe 的说明。"
- "近期有没有关于某中间件漏洞的官方公告？"
- "帮我看一下某产品最新发布说明。"
