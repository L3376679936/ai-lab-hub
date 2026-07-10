const readline = require('readline');
const fs = require('fs');
const path = require('path');
const { Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType } = require('docx');

// 创建 stdio 分行读取流
const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false
});

// 监听 Java 底座写入 stdin 的指令
rl.on('line', async (line) => {
    if (!line.trim()) return;
    try {
        const request = JSON.parse(line);
        await handleRequest(request);
    } catch (err) {
        sendErrorResponse(null, -32700, "Parse error: " + err.message);
    }
});

/**
 * 处理 JSON-RPC 2.0 请求
 */
async function handleRequest(req) {
    const { jsonrpc, method, params, id } = req;

    if (jsonrpc !== '2.0') {
        sendErrorResponse(id, -32600, "Invalid Request: Only JSON-RPC 2.0 is supported");
        return;
    }

    if (method === 'tools/list') {
        // 返回本 MCP 暴露出的 Word 导出工具规格
        sendSuccessResponse(id, {
            tools: [
                {
                    name: "write_docx",
                    description: "根据传入的段落和样式规范在指定路径生成精美的排版 Word 报告",
                    inputSchema: {
                        type: "object",
                        properties: {
                            outputPath: {
                                type: "string",
                                description: "导出的 docx 物理文件的绝对路径"
                            },
                            contentItems: {
                                type: "array",
                                description: "内容节点数组，包含标题、正文等",
                                items: {
                                    type: "object",
                                    required: ["type", "text"],
                                    properties: {
                                        type: {
                                            type: "string",
                                            enum: ["title", "subtitle", "h1", "h2", "paragraph"],
                                            description: "节点排版类型"
                                        },
                                        text: {
                                            type: "string",
                                            description: "文本内容"
                                        }
                                    }
                                }
                            }
                        },
                        required: ["outputPath", "contentItems"]
                    }
                }
            ]
        });
    } else if (method === 'tools/call') {
        if (!params || params.name !== 'write_docx') {
            sendErrorResponse(id, -32601, "Method not found: Tool name must be write_docx");
            return;
        }

        const args = params.arguments || {};
        const { outputPath, contentItems } = args;

        if (!outputPath || !contentItems) {
            sendErrorResponse(id, -32602, "Invalid params: Missing outputPath or contentItems");
            return;
        }

        try {
            await generateWordDocument(outputPath, contentItems);
            sendSuccessResponse(id, {
                content: [
                    {
                        type: "text",
                        text: "Word文档物理导出成功，路径: " + outputPath
                    }
                ],
                isError: false
            });
        } catch (err) {
            sendSuccessResponse(id, {
                content: [
                    {
                        type: "text",
                        text: "生成Word失败: " + err.message
                    }
                ],
                isError: true
            });
        }
    } else {
        sendErrorResponse(id, -32601, "Method not found: " + method);
    }
}

/**
 * 调用 docx 库物理组装 Word 文档
 */
async function generateWordDocument(outputPath, items) {
    // 确保父目录存在
    const dir = path.dirname(outputPath);
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }

    const docChildren = [];

    for (const item of items) {
        const { type, text } = item;

        if (type === 'title') {
            // 大标题：居中、加粗、字号 26pt (docx 库对应半磅值 52)、段后间距
            docChildren.push(
                new Paragraph({
                    alignment: AlignmentType.CENTER,
                    spacing: { before: 240, after: 360 },
                    children: [
                        new TextRun({
                            text: text,
                            bold: true,
                            size: 52, // 26pt
                            color: "1F2937", // 墨灰深色
                            font: "Microsoft YaHei"
                        })
                    ]
                })
            );
        } else if (type === 'subtitle') {
            // 副标题：居中、字号 14pt (28半磅)、段后间距
            docChildren.push(
                new Paragraph({
                    alignment: AlignmentType.CENTER,
                    spacing: { before: 0, after: 240 },
                    children: [
                        new TextRun({
                            text: text,
                            size: 28, // 14pt
                            color: "4B5563",
                            font: "Microsoft YaHei"
                        })
                    ]
                })
            );
        } else if (type === 'h1') {
            // 一级标题：大字号、加粗、段前段后间距、Heading1 等级
            docChildren.push(
                new Paragraph({
                    heading: HeadingLevel.HEADING_1,
                    spacing: { before: 360, after: 120 },
                    children: [
                        new TextRun({
                            text: text,
                            bold: true,
                            size: 36, // 18pt
                            color: "111827",
                            font: "Microsoft YaHei"
                        })
                    ]
                })
            );
        } else if (type === 'h2') {
            // 二级标题：Heading2 等级
            docChildren.push(
                new Paragraph({
                    heading: HeadingLevel.HEADING_2,
                    spacing: { before: 240, after: 120 },
                    children: [
                        new TextRun({
                            text: text,
                            bold: true,
                            size: 28, // 14pt
                            color: "374151",
                            font: "Microsoft YaHei"
                        })
                    ]
                })
            );
        } else if (type === 'paragraph') {
            // 正文段落：首行缩进 2 字符 (12pt字号下对应 240 dxa)，首行缩进配置：indent.firstLine
            docChildren.push(
                new Paragraph({
                    indent: { firstLine: 480 }, // 首行缩进 2 字符 (对应字号的倍数)
                    spacing: { before: 60, after: 120, line: 360 }, // 1.5 倍行距
                    children: [
                        new TextRun({
                            text: text,
                            size: 24, // 12pt (小四号)
                            color: "1F2937",
                            font: "Microsoft YaHei"
                        })
                    ]
                })
            );
        }
    }

    const doc = new Document({
        sections: [
            {
                properties: {},
                children: docChildren
            }
        ]
    });

    // 编译为 Buffer 并写入磁盘
    const buffer = await Packer.toBuffer(doc);
    fs.writeFileSync(outputPath, buffer);
}

function sendSuccessResponse(id, result) {
    console.log(JSON.stringify({
        jsonrpc: "2.0",
        result: result,
        id: id
    }));
}

function sendErrorResponse(id, code, message) {
    console.log(JSON.stringify({
        jsonrpc: "2.0",
        error: {
            code: code,
            message: message
        },
        id: id
    }));
}
