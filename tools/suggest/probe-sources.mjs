// 探测官方数据源可达性，并汇报各源能拿到什么
// 用法: node probe-sources.mjs
const SOURCES = [
  {
    name: 'bedrock-samples 仓库 meta（版本号）',
    url: 'https://raw.githubusercontent.com/Mojang/bedrock-samples/main/version.json',
  },
  {
    name: 'bedrock-samples 资源包语言文件目录（看有没有 zh_CN）',
    url: 'https://api.github.com/repos/Mojang/bedrock-samples/contents/resource_pack/texts',
  },
  {
    name: 'bedrock-samples 资源包根目录（看有哪些清单文件）',
    url: 'https://api.github.com/repos/Mojang/bedrock-samples/contents/resource_pack',
  },
  {
    name: 'bedrock-samples 行为包根目录',
    url: 'https://api.github.com/repos/Mojang/bedrock-samples/contents/behavior_pack',
  },
  {
    name: 'Mojang 官方 item 文档（Microsoft Learn 用）',
    url: 'https://raw.githubusercontent.com/MicrosoftDocs/minecraft-creator/main/creator/Reference/Content/ItemReference/Examples/ItemComponents/minecraft_damage.md',
  },
];

for (const s of SOURCES) {
  try {
    const res = await fetch(s.url, { headers: { 'User-Agent': 'cmdhelper-audit' } });
    const text = await res.text();
    console.log(`\n=== ${s.name}`);
    console.log(`    HTTP ${res.status}  长度 ${text.length}`);
    if (res.ok) {
      console.log('    ' + text.slice(0, 400).replace(/\n/g, '\n    '));
    }
  } catch (e) {
    console.log(`\n=== ${s.name}\n    失败: ${e.message}`);
  }
}
