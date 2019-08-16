package org.incredible.certProcessor.qrcode.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.commons.io.FileUtils;
import org.incredible.certProcessor.qrcode.QRCodeGenerationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.FontMetrics;
import java.awt.Font;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.awt.FontFormatException;
import java.io.*;
import java.util.*;

public class QRCodeImageGenerator {

    private static Logger logger = LoggerFactory.getLogger(QRCodeImageGenerator.class);

    static QRCodeWriter qrCodeWriter = new QRCodeWriter();


    public File createQRImages(QRCodeGenerationModel qrGenRequest) throws WriterException, IOException, NotFoundException, FontFormatException {

        List<File> fileList = new ArrayList<File>();

        String data = qrGenRequest.getData();
        String text = qrGenRequest.getText();
        String fileName = qrGenRequest.getFileName();

        String errorCorrectionLevel = qrGenRequest.getErrorCorrectionLevel();
        int pixelsPerBlock = qrGenRequest.getPixelsPerBlock();
        int qrMargin = qrGenRequest.getQrCodeMargin();
        String fontName = qrGenRequest.getTextFontName();
        int fontSize = qrGenRequest.getTextFontSize();
        double tracking = qrGenRequest.getTextCharacterSpacing();
        String imageFormat = qrGenRequest.getFileFormat();
        String colorModel = qrGenRequest.getColorModel();
        int borderSize = qrGenRequest.getImageBorderSize();
        int qrMarginBottom = qrGenRequest.getQrCodeMarginBottom();
        int imageMargin = qrGenRequest.getImageMargin();


        BufferedImage qrImage = generateBaseImage(data, errorCorrectionLevel, pixelsPerBlock, qrMargin, colorModel);

        if (null != text || "" != text) {
            BufferedImage textImage = getTextImage(text, fontName, fontSize, tracking, colorModel);
            qrImage = addTextToBaseImage(qrImage, textImage, colorModel, qrMargin, pixelsPerBlock, qrMarginBottom, imageMargin);
        }

        if (borderSize > 0) {
            drawBorder(qrImage, borderSize, imageMargin);
        }

        File finalImageFile = new File(fileName + "." + imageFormat);
        ImageIO.write(qrImage, imageFormat, finalImageFile);
        fileList.add(finalImageFile);
        logger.info("qr code is created for the certificate");
        return finalImageFile;

    }

    private static BufferedImage addTextToBaseImage(BufferedImage qrImage, BufferedImage textImage, String colorModel, int qrMargin, int pixelsPerBlock, int qrMarginBottom, int imageMargin) throws NotFoundException {
        BufferedImageLuminanceSource qrSource = new BufferedImageLuminanceSource(qrImage);
        HybridBinarizer qrBinarizer = new HybridBinarizer(qrSource);
        BitMatrix qrBits = qrBinarizer.getBlackMatrix();

        BufferedImageLuminanceSource textSource = new BufferedImageLuminanceSource(textImage);
        HybridBinarizer textBinarizer = new HybridBinarizer(textSource);
        BitMatrix textBits = textBinarizer.getBlackMatrix();

        if (qrBits.getWidth() > textBits.getWidth()) {
            BitMatrix tempTextMatrix = new BitMatrix(qrBits.getWidth(), textBits.getHeight());
            copyMatrixDataToBiggerMatrix(textBits, tempTextMatrix);
            textBits = tempTextMatrix;
        } else if (qrBits.getWidth() < textBits.getWidth()) {
            BitMatrix tempQrMatrix = new BitMatrix(textBits.getWidth(), qrBits.getHeight());
            copyMatrixDataToBiggerMatrix(qrBits, tempQrMatrix);
            qrBits = tempQrMatrix;
        }

        BitMatrix mergedMatrix = mergeMatricesOfSameWidth(qrBits, textBits, qrMargin, pixelsPerBlock, qrMarginBottom, imageMargin);
        return getImage(mergedMatrix, colorModel);
    }

    private static BufferedImage generateBaseImage(String data, String errorCorrectionLevel, int pixelsPerBlock, int qrMargin, String colorModel) throws WriterException {
        Map hintsMap = getHintsMap(errorCorrectionLevel, qrMargin);
        BitMatrix defaultBitMatrix = getDefaultBitMatrix(data, hintsMap);
        BitMatrix largeBitMatrix = getBitMatrix(data, defaultBitMatrix.getWidth() * pixelsPerBlock, defaultBitMatrix.getHeight() * pixelsPerBlock, hintsMap);
        BufferedImage qrImage = getImage(largeBitMatrix, colorModel);
        return qrImage;
    }

    //To remove extra spaces between text and qrcode, margin below qrcode is removed
    //Parameter, qrCodeMarginBottom, is introduced to add custom margin(in pixels) between qrcode and text
    //Parameter, imageMargin is introduced, to add custom margin(in pixels) outside the black border of the image
    private static BitMatrix mergeMatricesOfSameWidth(BitMatrix firstMatrix, BitMatrix secondMatrix, int qrMargin, int pixelsPerBlock, int qrMarginBottom, int imageMargin) {
        int mergedWidth = firstMatrix.getWidth() + (2 * imageMargin);
        int mergedHeight = firstMatrix.getHeight() + secondMatrix.getHeight() + (2 * imageMargin);
        int defaultBottomMargin = pixelsPerBlock * qrMargin;
        int marginToBeRemoved = qrMarginBottom > defaultBottomMargin ? 0 : (defaultBottomMargin - qrMarginBottom);
        BitMatrix mergedMatrix = new BitMatrix(mergedWidth, mergedHeight - marginToBeRemoved);

        for (int x = 0; x < firstMatrix.getWidth(); x++) {
            for (int y = 0; y < firstMatrix.getHeight() - marginToBeRemoved; y++) {
                if (firstMatrix.get(x, y)) {
                    mergedMatrix.set(x + imageMargin, y + imageMargin);
                }
            }
        }
        for (int x = 0; x < secondMatrix.getWidth(); x++) {
            for (int y = 0; y < secondMatrix.getHeight(); y++) {
                if (secondMatrix.get(x, y)) {
                    mergedMatrix.set(x + imageMargin, y + firstMatrix.getHeight() - marginToBeRemoved + imageMargin);
                }
            }
        }
        return mergedMatrix;
    }

    private static void copyMatrixDataToBiggerMatrix(BitMatrix fromMatrix, BitMatrix toMatrix) {
        int widthDiff = toMatrix.getWidth() - fromMatrix.getWidth();
        int leftMargin = widthDiff / 2;
        for (int x = 0; x < fromMatrix.getWidth(); x++) {
            for (int y = 0; y < fromMatrix.getHeight(); y++) {
                if (fromMatrix.get(x, y)) {
                    toMatrix.set(x + leftMargin, y);
                }
            }
        }
    }

    private static void drawBorder(BufferedImage image, int borderSize, int imageMargin) {
        image.createGraphics();
        Graphics2D graphics = (Graphics2D) image.getGraphics();
        graphics.setColor(Color.BLACK);
        for (int i = 0; i < borderSize; i++) {
            graphics.drawRect(i + imageMargin, i + imageMargin, image.getWidth() - 1 - (2 * i) - (2 * imageMargin), image.getHeight() - 1 - (2 * i) - (2 * imageMargin));
        }
        graphics.dispose();
    }

    private static BufferedImage getImage(BitMatrix bitMatrix, String colorModel) {
        int imageWidth = bitMatrix.getWidth();
        int imageHeight = bitMatrix.getHeight();
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, getImageType(colorModel));
        image.createGraphics();

        Graphics2D graphics = (Graphics2D) image.getGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, imageWidth, imageHeight);

        graphics.setColor(Color.BLACK);

        for (int i = 0; i < imageWidth; i++) {
            for (int j = 0; j < imageHeight; j++) {
                if (bitMatrix.get(i, j)) {
                    graphics.fillRect(i, j, 1, 1);
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private static BitMatrix getBitMatrix(String data, int width, int height, Map hintsMap) throws WriterException {
        BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height, hintsMap);
        return bitMatrix;
    }

    private static BitMatrix getDefaultBitMatrix(String data, Map hintsMap) throws WriterException {
        BitMatrix defaultBitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 0, 0, hintsMap);
        return defaultBitMatrix;
    }

    private static Map getHintsMap(String errorCorrectionLevel, int qrMargin) {
        Map hintsMap = new HashMap();
        switch (errorCorrectionLevel) {
            case "H":
                hintsMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
                break;
            case "Q":
                hintsMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);
                break;
            case "M":
                hintsMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
                break;
            case "L":
                hintsMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
                break;
        }
        hintsMap.put(EncodeHintType.MARGIN, qrMargin);
        return hintsMap;
    }

    //Sample = 2A42UH , Verdana, 11, 0.1, Grayscale
    private BufferedImage getTextImage(String text, String fontName, int fontSize, double tracking, String colorModel) throws IOException, FontFormatException {
        BufferedImage image = new BufferedImage(1, 1, getImageType(colorModel));
        //Font basicFont = new Font(fontName, Font.BOLD, fontSize);
        String fontFile = fontName + ".ttf";
        logger.info("qr code font file name : " + fontFile);
        File file = new File("conf/");
        List<File> filesList = (List<File>) FileUtils.listFiles(file, new String[]{"ttf", "conf"}, true);

        Font basicFont = Font.createFont(Font.TRUETYPE_FONT, getFontFile(filesList));

        Map<TextAttribute, Object> attributes = new HashMap<TextAttribute, Object>();
        attributes.put(TextAttribute.TRACKING, tracking);
        attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
        attributes.put(TextAttribute.SIZE, fontSize);
        Font font = basicFont.deriveFont(attributes);

        Graphics2D graphics2d = image.createGraphics();
        graphics2d.setFont(font);
        FontMetrics fontmetrics = graphics2d.getFontMetrics();
        int width = fontmetrics.stringWidth(text);
        int height = fontmetrics.getHeight();
        graphics2d.dispose();

        image = new BufferedImage(width, height, getImageType(colorModel));
        graphics2d = image.createGraphics();
        graphics2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        graphics2d.setColor(Color.WHITE);
        graphics2d.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics2d.setColor(Color.BLACK);

        graphics2d.setFont(font);
        fontmetrics = graphics2d.getFontMetrics();
        graphics2d.drawString(text, 0, fontmetrics.getAscent());
        graphics2d.dispose();

        return image;
    }

    private static int getImageType(String colorModel) {
        if (colorModel.equalsIgnoreCase("RGB")) {
            return BufferedImage.TYPE_INT_RGB;
        } else {
            return BufferedImage.TYPE_BYTE_GRAY;
        }
    }

    private  InputStream getFontFile(List<File> files) throws FileNotFoundException {

        Iterator<File> iterator = files.iterator();
        logger.info("File list : "+ files.size());
        InputStream fontStream=null;
        while (iterator.hasNext()) {
            File file = iterator.next();
            logger.info("Font file name" + file.getName() +  "File absolute path" + file.getAbsolutePath());

            if (file.getName().equals("Verdana.ttf")) {
                fontStream = new FileInputStream(file);
                logger.info("Font file name" + file.getName() +  "File absolute path" + file.getAbsolutePath());
            }    }
            return fontStream;
    }
}
