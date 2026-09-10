package com.sinosig.sluw.application.tools;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
public class WebPageReader extends JFrame{


    private final JTextArea contentArea;
    private final JTextField urlField;
    private final JButton fetchButton;
    private final JLabel statusLabel;

    public WebPageReader() {
        setTitle("百度Comate网页内容读取器");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // 顶部面板
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        topPanel.setBackground(new Color(240, 245, 255));

        // URL输入区域
        JPanel urlPanel = new JPanel(new BorderLayout(10, 10));
        JLabel urlLabel = new JLabel("目标网址:");
        urlLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));

        urlField = new JTextField("https://comate.baidu.com/zh/page/tpra4xpc4v0");
        urlField.setFont(new Font("微软雅黑", Font.PLAIN, 14));

        fetchButton = new JButton("获取网页内容");
        fetchButton.setFont(new Font("微软雅黑", Font.BOLD, 14));
        fetchButton.setBackground(new Color(65, 105, 225));
        fetchButton.setForeground(Color.WHITE);
        fetchButton.setFocusPainted(false);

        fetchButton.addActionListener(e -> fetchWebContent());

        urlPanel.add(urlLabel, BorderLayout.WEST);
        urlPanel.add(urlField, BorderLayout.CENTER);
        urlPanel.add(fetchButton, BorderLayout.EAST);

        // 状态标签
        statusLabel = new JLabel("就绪");
        statusLabel.setFont(new Font("微软雅黑", Font.ITALIC, 12));
        statusLabel.setForeground(Color.GRAY);

        topPanel.add(urlPanel, BorderLayout.CENTER);
        topPanel.add(statusLabel, BorderLayout.SOUTH);

        // 内容区域
        contentArea = new JTextArea();
        contentArea.setEditable(false);
        contentArea.setFont(new Font("等线", Font.PLAIN, 14));
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(contentArea);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 15, 15, 15),
                BorderFactory.createLineBorder(new Color(200, 200, 200))
        ));

        // 添加组件到主窗口
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // 添加底部信息
        JLabel footerLabel = new JLabel("© 2023 百度Comate网页内容阅读器 | 使用Java HttpClient技术", SwingConstants.CENTER);
        footerLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        footerLabel.setForeground(new Color(100, 100, 100));
        footerLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        footerLabel.setBackground(new Color(245, 245, 245));
        footerLabel.setOpaque(true);
        add(footerLabel, BorderLayout.SOUTH);
    }

    private void fetchWebContent() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入有效的URL", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        statusLabel.setText("正在获取内容...");
        fetchButton.setEnabled(false);
        contentArea.setText("");

        // 使用线程避免阻塞UI
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String content = response.body();

                    // 在UI线程更新内容
                    SwingUtilities.invokeLater(() -> {
                        contentArea.setText(content);
                        statusLabel.setText("获取成功! 响应代码: 200 - 内容长度: " + content.length() + " 字符");
                        fetchButton.setEnabled(true);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        contentArea.setText("错误: 服务器返回状态码 " + response.statusCode());
                        statusLabel.setText("获取失败: HTTP " + response.statusCode());
                        fetchButton.setEnabled(true);
                    });
                }
            } catch (IOException | InterruptedException e) {
                SwingUtilities.invokeLater(() -> {
                    contentArea.setText("发生错误: " + e.getMessage());
                    statusLabel.setText("错误: " + e.getClass().getSimpleName());
                    fetchButton.setEnabled(true);
                });
            } catch (IllegalArgumentException e) {
                SwingUtilities.invokeLater(() -> {
                    contentArea.setText("无效的URL: " + e.getMessage());
                    statusLabel.setText("错误: 无效的URL格式");
                    fetchButton.setEnabled(true);
                });
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 设置系统外观
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            WebPageReader reader = new WebPageReader();
            reader.setLocationRelativeTo(null); // 居中显示
            reader.setVisible(true);
        });
    }
}
