package com.sinosig.sluw.application.tools;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class HttpsToolService {
//
//        private Site site = Site.me().setRetryTimes(3).setSleepTime(1000).setTimeOut(10000);
//
//        @Override
//        public Site getSite() {
//            return site;
//        }
//
//        @Override
//        public void process(Page page) {
//                if (!page.getUrl().regex("https://www.hbu.edu.cn/info.*").match()) {
//                    //获取新闻URL
//                    List<String> all = page.getHtml().xpath("/html/body/div[@class='g-row']/div[1]/div[@class='col_r']/ul[@class='ul-study']/li/a").links().all();
//                    String nextPage = page.getHtml().xpath("/html/body/div[@class='g-row']/div[1]/div[@class='col_r']/div[@class='pb_sys_common pb_sys_normal pb_sys_style1']/span[2]/span[@class='p_next p_fun']/a").links().get();
//                    all.add(nextPage);
//                    //添加
//                    page.addTargetRequests(all);
//                } else {//新闻详情页
//                    String url = page.getUrl().toString();
//                    String title = page.getHtml().xpath("/html/body/div[@class='g-row']/div[1]/div[@class='col-l']/form/div[1]/h1/text()").get();
//                    String date = page.getHtml().xpath("/html/body/div[@class='g-row']/div[1]/div[@class='col-l']/form/div[1]/div[@class='date']/text()").get();
//                    String content = page.getHtml().xpath("/html/body/div[@class='g-row']/div[1]/div[@class='col-l']/form/div[1]/div[@class='txt']/allText()").get();
//                    //存储结果
//                    page.putField("地址", url);
//                    page.putField("标题", title);
//                    page.putField("日期", date);
//                    page.putField("内容", content);
//                }
//        }
//    // 创建支持多种协议的 HttpClient
//    private static CloseableHttpClient createCustomHttpClient() throws NoSuchAlgorithmException {
//        SSLContext sslContext = SSLContext.getInstance("TLS");
//
//        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
//                sslContext,
//                new String[]{"TLSv1.2", "TLSv1.1", "TLSv1"}, // 支持的协议
//                null, // 支持的加密套件
//                SSLConnectionSocketFactory.getDefaultHostnameVerifier()
//        );
//
//        return HttpClients.custom()
//                .setSSLSocketFactory(sslsf)
//                .build();
//    }
//    // 在爬虫初始化中使用
//    static HttpClientDownloader downloader = new HttpClientDownloader() {
//        public CloseableHttpClient getHttpClient(Site site) {
//            try {
//                return createCustomHttpClient();
//            } catch (Exception e) {
//                throw new RuntimeException("Failed to create HttpClient", e);
//            }
//        }
//    };
//        public static void main(String[] args) {
//                Spider.create(new HttpsToolService())
//                        //初始访问url地址
//                        .addUrl("https://www.hbu.edu.cn/info/1162/21047.htm")
//                        .setDownloader(new HttpClientDownloader())
//                        .addPipeline(new JsonFilePipeline("HttpsToolService/"))
//                        .thread(5)
//                        .run();
//
//        }
//
    }