/**
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * 
 * @author Nicolas Marcotte
 */
package ca.usherbrooke.sti.si.html.sub.report.jasper;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import net.sf.jasperreports.engine.design.JasperDesign;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xhtmlrenderer.extend.FontContext;
import org.xhtmlrenderer.extend.NamespaceHandler;
import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.extend.UserInterface;
import org.xhtmlrenderer.layout.BoxBuilder;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.render.Box;
import org.xhtmlrenderer.render.FSFont;
import org.xhtmlrenderer.render.FSFontMetrics;
import org.xhtmlrenderer.render.ViewportBox;
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler;
import org.xhtmlrenderer.swing.AWTFontResolver;
import org.xhtmlrenderer.swing.Java2DFontContext;
import org.xhtmlrenderer.swing.Java2DOutputDevice;
import org.xhtmlrenderer.swing.Java2DTextRenderer;
import org.xhtmlrenderer.swing.NaiveUserAgent;
import org.xhtmlrenderer.swing.SwingReplacedElementFactory;
import org.xhtmlrenderer.util.Configuration;
import org.xhtmlrenderer.util.ImageUtil;

/**
 * <p>
 * Renders an XML files, formatted with CSS, as an image. Input is a document in the form of file or URL,
 * and output is a BufferedImage. A Java2DRenderer is not intended to be re-used for multiple document
 * sources; just create new Java2DRenderers for each one you need. Java2DRenderer is not thread-safe.
 * Standard usage pattern is</p>
 *
 * <pre>
 * File xhtml = ...
 * Java2DRenderer rend = new Java2DRenderer(xhtml);
 * BufferedImage image = rend.getImage();
 * </pre>
 *
 * <p>
 * The document is not loaded, and layout and render don't take place, until {@link #getImage(int)} is called.
 * Subsequent calls to {@link #getImage()} don't result in a reload; create a new Java2DRenderer instance to do so.</p>
 *
 * <p>
 * As with {@link org.xhtmlrenderer.swing.RootPanel}, you can access the
 * {@link org.xhtmlrenderer.layout.SharedContext} instance that will be used by this renderer and change settings
 * to control the rendering process; use {@link #getSharedContext()}.</p>
 *
 * <p>
 * By default, this renderer will render to an RGB image which does not support transparency. To use another type
 * of BufferedImage, either set the image type using {@link #setBufferedImageType(int)} before calling
 * {@link #getImage()}, or else override the {@link #createBufferedImage(int, int)} to have full control over
 * the image we render to.</p>
 *
 * <p>
 * Not thread-safe.</p>
 *
 * @see ITextRenderer
 */
public class JRRenderer {

    private static final int DEFAULT_HEIGHT = 572;
    private static final int DEFAULT_DOTS_PER_POINT = 1;
    private static final int DEFAULT_DOTS_PER_PIXEL = 1;
    private static final int DEFAULT_IMAGE_TYPE = BufferedImage.TYPE_USHORT_GRAY;
     public static final float JASPER_TEXT_SCALE_FACTOR = 1.1f;

    private SharedContext sharedContext;
    private Java2DOutputDevice outputDevice;

    private Document doc;
    private Box root;

    private float dotsPerPoint = DEFAULT_DOTS_PER_POINT;
    private BufferedImage outputImage;
    private int bufferedImageType;

    /**
     * Whether we've completed rendering; image will only be rendered once.
     */
    private boolean rendered;
    private String sourceDocument;
    private String sourceDocumentBase;
    private int width;
    private int height;
    private static final int NO_HEIGHT = -1;
    private Map renderingHints;
    private final FStoJR fsTojr;
    private LayoutContext layoutContext;

    /**
     * Creates a new instance pointing to the given Document. Does not render until {@link #getImage(int)} is called for
     * the first time.
     *
     * @param doc    The document to be rendered.
     * @param width  Target width, in pixels, for the image; required to provide horizontal bounds for the layout.
     * @param height Target height, in pixels, for the image.
     */
    public JRRenderer(Document doc, int width) {
        this.bufferedImageType = DEFAULT_IMAGE_TYPE;
        this.doc = doc;
        this.width = width;
        this.height = NO_HEIGHT;
        this.fsTojr = new FStoJR();
        init(DEFAULT_DOTS_PER_PIXEL, DEFAULT_DOTS_PER_POINT);

    }

    /**
     * Sets the rendering hints to apply to the Graphics2D instance used by the renderer; see
     * {@link java.awt.Graphics2D#setRenderingHints(java.util.Map)}. The Map need not specify values for all
     * properties; any settings in this map will be applied as override to the default settings, and will
     * not replace the entire Map for the Graphics2D instance.
     *
     * @param hints values to override in default rendering hints for Graphics2D we are rendering to
     */
    public void setRenderingHints(Map hints) {
        renderingHints = hints;
    }

    /**
     * Sets the type for the BufferedImage used as output for this renderer; must be one of the values from
     * {@link java.awt.image.BufferedImage} allowed in that class' constructor as a type argument. See docs for
     * the type parameter in {@link java.awt.image.BufferedImage#BufferedImage(int, int, int)}. Defaults to RGB with
     * no support for transparency. The type is used when the image is first created, so to change the default type
     * do so before calling {@link #getImage()}.
     *
     * @param bufferedImageType the BufferedImage type to be used to create the image on which the document
     *                          will be rendered.
     */
    public void setBufferedImageType(int bufferedImageType) {
        this.bufferedImageType = bufferedImageType;
    }

    /**
     * Returns the SharedContext to be used by renderer. Is instantiated along with the class, so can be accessed
     * before {@link #getImage()} is called to tune the rendering process.
     *
     * @return the SharedContext instance that will be used by this renderer
     */
    public SharedContext getSharedContext() {
        return sharedContext;
    }

    /**
     * Returns a BufferedImage using the specified width and height. By default this returns an image compatible
     * with the screen (if not in "headless" mode) using the BufferedImage type specified in
     * {@link #setBufferedImageType(int)}, or else RGB if none if specified.
     *
     * @param width  target width
     * @param height target height
     * @return new BI
     */
    protected BufferedImage createBufferedImage(int width, int height) {
        BufferedImage image = ImageUtil.createCompatibleBufferedImage(width, height, this.bufferedImageType);
        ImageUtil.clearImage(image);
        return image;
    }

    private void setDocument(Document doc, String url, NamespaceHandler nsh) {
        this.doc = doc;

        sharedContext.reset();
        if (Configuration.isTrue("xr.cache.stylesheets", true)) {
            sharedContext.getCss().flushStyleSheets();
        } else {
            sharedContext.getCss().flushAllStyleSheets();
        }
        sharedContext.setBaseURL(url);
        sharedContext.setNamespaceHandler(nsh);
        sharedContext.getCss().setDocumentContext(
                sharedContext,
                sharedContext.getNamespaceHandler(),
                doc,
                new NullUserInterface()
        );
    }

    private void layout(int width) {
        Rectangle rect = new Rectangle(0, 0, width, DEFAULT_HEIGHT);
        sharedContext.set_TempCanvas(rect);
        layoutContext = newLayoutContext();
        BlockBox root = BoxBuilder.createRootBox(layoutContext, doc);

        root.setContainingBlock(new ViewportBox(rect));
        root.layout(layoutContext);
        this.root = root;

    }

    private Document loadDocument(final String uri) {
        return sharedContext.getUac().getXMLResource(uri).getDocument();
    }

    private LayoutContext newLayoutContext() {
        LayoutContext result = sharedContext.newLayoutContextInstance();
        result.setFontContext(new Java2DFontContext(outputDevice.getGraphics()));

        sharedContext.getTextRenderer().setup(result.getFontContext());

        return result;
    }

    private void init(float dotsPerPoint, int dotsPerPixel) {
        this.dotsPerPoint = dotsPerPoint;
        outputImage = ImageUtil.createCompatibleBufferedImage(DEFAULT_DOTS_PER_POINT, DEFAULT_DOTS_PER_POINT);
        outputDevice = new Java2DOutputDevice(outputImage) {

        };
        UserAgentCallback userAgent = new NaiveUserAgent();
        sharedContext = new SharedContext(userAgent);
        AWTFontResolver fontResolver = new AWTFontResolver();
     
        sharedContext.setFontResolver(fontResolver);
        sharedContext.setTextRenderer(new Java2DTextRenderer() {

            @Override
            public FSFontMetrics getFSFontMetrics(FontContext fc, FSFont font, String string) {
                FSFontMetrics retval = super.getFSFontMetrics(fc, font, string);
                retval = new FSFontMetricsScaleforJasper(retval);
                return retval;
            }
        });

        SwingReplacedElementFactory replacedElementFactory = new SwingReplacedElementFactory();
        sharedContext.setReplacedElementFactory(replacedElementFactory);
        sharedContext.setDPI(72 * this.dotsPerPoint);
        sharedContext.setDotsPerPixel(dotsPerPixel);
        sharedContext.setPrint(false);
        sharedContext.setInteractive(false);
    }

    public JasperDesign buildReport() {
        setDocument((doc == null ? loadDocument(sourceDocument) : doc), sourceDocumentBase, new XhtmlNamespaceHandler());
    
        layout(this.width);
        fsTojr.setHeight(root.getHeight());
        new BoxVisitor(fsTojr,layoutContext).convertTree(root,  0);

        
        return fsTojr.build();
    }

    private static final class NullUserInterface implements UserInterface {

        @Override
        public boolean isHover(Element e) {
            return false;
        }

        @Override
        public boolean isActive(Element e) {
            return false;
        }

        @Override
        public boolean isFocus(Element e) {
            return false;
        }
    }



    private static class FSFontMetricsScaleforJasper implements FSFontMetrics {

        private FSFontMetrics delegate;

        public FSFontMetricsScaleforJasper() {
        }

        private FSFontMetricsScaleforJasper(FSFontMetrics retval) {
            this.delegate = retval;
        }

        @Override
        public float getAscent() {
            return delegate.getAscent()*JASPER_TEXT_SCALE_FACTOR;
        }
      
        @Override
        public float getDescent() {
            return delegate.getDescent();
        }

        @Override
        public float getStrikethroughOffset() {
            return delegate.getStrikethroughOffset();
        }

        @Override
        public float getStrikethroughThickness() {
            return delegate.getStrikethroughThickness();
        }

        @Override
        public float getUnderlineOffset() {
            return delegate.getUnderlineOffset();
        }

        @Override
        public float getUnderlineThickness() {
            return delegate.getUnderlineThickness();
        }

    }
    
     
}
