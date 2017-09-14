/*
 * $Id: DownloadAction.java 164530 2005-04-25 03:11:07Z niallp $
 *
 * Copyright 2004-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flink.ccasbank.web;

import org.apache.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.flink.ccasbank.common.util.FileUtil;
import com.flink.ccasbank.exception.ApplicationException;
import com.flink.ccasbank.web.common.ErrorView;
import com.flink.ccasbank.web.common.RequestManager;

/**
 * This is an abstract base class that minimizes the amount of special coding that needs to be written to download a file. All that is required to use this class is to extend it and implement the <code>getStreamInfo()</code> method so that it returns the relevant information for the file (or other stream) to be downloaded. Optionally, the
 * <code>getBufferSize()</code> method may be overridden to customize the size of the buffer used to transfer the file.
 * 
 * @since Struts 1.2.6
 */
public class DownloadAction extends Action
{
    /**
     * 
     */
    public static final String FILE_KEY = "file";

    public static final String DOWNLOAD_KEY = "downloadKey";

    /**
     * Log4J Logger for this class
     */
    private static final Logger logger = Logger.getLogger(DownloadAction.class);

    /**
     * If the <code>getBufferSize()</code> method is not overridden, this is the buffer size that will be used to transfer the data to the servlet output stream.
     */
    protected static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    /**
     * Returns the information on the file, or other stream, to be downloaded by this action. This method must be implemented by an extending class.
     * 
     * @param mapping
     *            The ActionMapping used to select this instance.
     * @param form
     *            The optional ActionForm bean for this request (if any).
     * @param request
     *            The HTTP request we are processing.
     * @param response
     *            The HTTP response we are creating.
     * 
     * @return The information for the file to be downloaded.
     * 
     * @throws Exception
     *             if an exception occurs.
     */
    protected StreamInfo getStreamInfo(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception
    {

        String filePath = (String) request.getAttribute(FILE_KEY);
        if (null == filePath || 0 == filePath.length())
        {
            filePath = request.getParameter(FILE_KEY);
        }
        if (null == filePath || 0 == filePath.length())
        {
            throw new Exception("The path of the file to be downloaded is null. ");
        }

        String contentType = null; // 不指定contentType

        contentType = (String) request.getAttribute("contentType");
        if (null == contentType || 0 == contentType.length())
        {
            contentType = "application/octet-stream";
        }

        return new FileStreamInfo(contentType, filePath);
    }

    /**
     * Returns the size of the buffer to be used in transferring the data to the servlet output stream. This method may be overridden by an extending class in order to customize the buffer size.
     * 
     * @return The size of the transfer buffer, in bytes.
     */
    protected int getBufferSize()
    {
        return DEFAULT_BUFFER_SIZE;
    }

    /**
     * Process the specified HTTP request, and create the corresponding HTTP response (or forward to another web component that will create it). Return an <code>ActionForward</code> instance describing where and how control should be forwarded, or <code>null</code> if the response has already been completed.
     * 
     * @param mapping
     *            The ActionMapping used to select this instance.
     * @param form
     *            The optional ActionForm bean for this request (if any).
     * @param request
     *            The HTTP request we are processing.
     * @param response
     *            The HTTP response we are creating.
     * 
     * @throws Exception
     *             if an exception occurs.
     */
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception
    {

        StreamInfo info = null;
        String contentType = null;
        InputStream stream = null;
        OutputStream fileOutStream = null;

        try
        {

            String filePath = (String) request.getAttribute(FILE_KEY);
            if (null == filePath || 0 == filePath.length())
            {
                filePath = request.getParameter(FILE_KEY);
            }
            if (null == filePath || 0 == filePath.length())
            {
                throw new Exception("The path of the file to be downloaded is null. ");
            }
            String fileName = FileUtil.getFileName(filePath);
            logger.debug("The path of the file to be downloaded is [" + filePath + "]. The file name is [" + fileName + "]. ");

            info = getStreamInfo(mapping, form, request, response);
            // logger.debug("1");
            contentType = info.getContentType();
            // logger.debug("2:contentType ===== " + contentType);
            stream = info.getInputStream();
            // logger.debug("3");

            response.setContentType(contentType);
            // logger.debug("4");
            fileName = new String(fileName.getBytes("gb2312"), "iso8859-1");

            response.setHeader("Content-disposition", "attachment; filename=" + fileName);

            //response.setHeader("Cache-Control", "private");// 此设置可以使文件下载时直接打开 
            response.setHeader("Pragma", "public");
            response.setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
            response.setHeader("Cache-Control", "public");

            // logger.debug("5");
            // response.sendRedirect("/accountFile/AccountFileAction.do?method=ViewExport");

            fileOutStream = response.getOutputStream();
            copy(stream, fileOutStream);

            // 设置是否下载完成变量,供页面刷新用
            String downKey = (String) request.getAttribute(DownloadAction.DOWNLOAD_KEY);
            if (null != downKey)
            {
                request.getSession().setAttribute(downKey, "downloadComplete");
            }
            // logger.debug("6");

        }
        catch (ApplicationException e)
        {
            String err = "Failed to download the file. " + e.getMessage();
            RequestManager.getInstance().setError(request, new ErrorView(err));
            return mapping.findForward("OperationFail");
        }

        // Tell Struts that we are done with the response.
        return null;
    }

    /**
     * Copy bytes from an <code>InputStream</code> to an <code>OutputStream</code>.
     * 
     * @param input
     *            The <code>InputStream</code> to read from.
     * @param output
     *            The <code>OutputStream</code> to write to.
     * 
     * @return the number of bytes copied
     * @throws ApplicationException
     * 
     * @throws IOException
     *             In case of an I/O problem
     */
    public int copy(InputStream input, OutputStream output) throws ApplicationException
    {
        byte[] buffer = new byte[getBufferSize()];
        int count = 0;
        int n = 0;
        try
        {
            while (-1 != (n = input.read(buffer)))
            {
                output.write(buffer, 0, n);
                count += n;
            }
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        finally
        {
            if (input != null)
            {
                try
                {
                    input.close();
                }
                catch (IOException e)
                {
                    throw new ApplicationException(e);
                }
            }
            if (null != output)
            {
                try
                {
                    output.flush();
                    output.close();
                }
                catch (IOException e)
                {
                    throw new ApplicationException(e);
                }

            }

        }
        return count;
    }

    /**
     * The information on a file, or other stream, to be downloaded by the <code>DownloadAction</code>.
     */
    public static interface StreamInfo
    {

        /**
         * Returns the content type of the stream to be downloaded.
         * 
         * @return The content type of the stream.
         */
        public abstract String getContentType();

        /**
         * Returns an input stream on the content to be downloaded. This stream will be closed by the <code>DownloadAction</code>.
         * 
         * @return The input stream for the content to be downloaded.
         */
        public abstract InputStream getInputStream() throws IOException;
    }

    /**
     * A concrete implementation of the <code>StreamInfo</code> interface which simplifies the downloading of a file from the disk.
     */
    public static class FileStreamInfo implements StreamInfo
    {
        /**
         * Log4J Logger for this class
         */
        private static final Logger logger = Logger.getLogger(FileStreamInfo.class);

        /**
         * The content type for this stream.
         */
        private String contentType;

        /**
         * The file to be downloaded.
         */
        private String file;

        /**
         * Constructs an instance of this class, based on the supplied parameters.
         * 
         * @param contentType
         *            The content type of the file.
         * @param file
         *            The file to be downloaded.
         */
        public FileStreamInfo(String contentType, String file)
        {
            this.contentType = contentType;
            this.file = file;
        }

        /**
         * Returns the content type of the stream to be downloaded.
         * 
         * @return The content type of the stream.
         */
        public String getContentType()
        {
            return this.contentType;
        }

        /**
         * Returns an input stream on the file to be downloaded. This stream will be closed by the <code>DownloadAction</code>.
         * 
         * @return The input stream for the file to be downloaded.
         */
        public InputStream getInputStream() throws IOException
        {
            InputStream fis = FileUtil.getInputStream(file);
            if (null == fis)
            {
                throw new IOException("Failed to read the file [" + file + "]. Please make sure that the file does exist. ");
            }
            BufferedInputStream bis = new BufferedInputStream(fis);
            return bis;
        }
    }

    /**
     * A concrete implementation of the <code>StreamInfo</code> interface which simplifies the downloading of a web application resource.
     */
    public static class ResourceStreamInfo implements StreamInfo
    {
        /**
         * Log4J Logger for this class
         */
        private static final Logger logger = Logger.getLogger(ResourceStreamInfo.class);

        /**
         * The content type for this stream.
         */
        private String contentType;

        /**
         * The servlet context for the resource to be downloaded.
         */
        private ServletContext context;

        /**
         * The path to the resource to be downloaded.
         */
        private String path;

        /**
         * Constructs an instance of this class, based on the supplied parameters.
         * 
         * @param contentType
         *            The content type of the file.
         * @param context
         *            The servlet context for the resource.
         * @param path
         *            The path to the resource to be downloaded.
         */
        public ResourceStreamInfo(String contentType, ServletContext context, String path)
        {
            this.contentType = contentType;
            this.context = context;
            this.path = path;
        }

        /**
         * Returns the content type of the stream to be downloaded.
         * 
         * @return The content type of the stream.
         */
        public String getContentType()
        {
            return this.contentType;
        }

        /**
         * Returns an input stream on the resource to be downloaded. This stream will be closed by the <code>DownloadAction</code>.
         * 
         * @return The input stream for the resource to be downloaded.
         */
        public InputStream getInputStream() throws IOException
        {
            return context.getResourceAsStream(path);
        }
		C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3
  }
}
