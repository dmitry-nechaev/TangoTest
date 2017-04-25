package com.mercdev.tangotest;

import android.opengl.GLES20;
import android.support.annotation.IntRange;
import android.support.annotation.Size;

import org.rajawali3d.BufferInfo;
import org.rajawali3d.Object3D;

import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * Created by nechaev on 02.03.2017.
 */

public class FixtureRectangularPrism extends Object3D {

    private static final int[] TOP_VERTEX_INDEXES = {0, 1, 4, 7, 10, 11, 12, 13, 16, 17, 18, 19};

    public class Facet {
        private int[] vertexIndexes;
        private int[] normalIndexes;

        public Facet(@Size(12) int[] vertexIndexes, @Size(3) int[] normalIndexes) {
            this.vertexIndexes = vertexIndexes;
            this.normalIndexes = normalIndexes;
        }

        public double[] getNormal() {
            FloatBuffer normals = getGeometry().getNormals();
            return new double[] {normals.get(normalIndexes[0]),
                                 normals.get(normalIndexes[1]),
                                 normals.get(normalIndexes[2]),
                                 1.0};
        }

        public double[] getVertex(@IntRange(from = 0, to = 3) int indexVertex) {
            FloatBuffer vertices = getGeometry().getVertices();
            return new double[] {vertices.get(vertexIndexes[indexVertex * 3]),
                                 vertices.get(vertexIndexes[indexVertex * 3 + 1]),
                                 vertices.get(vertexIndexes[indexVertex * 3 + 2]),
                                 1.0};
        }
    }

    private float mWidth;
    private float mHeight;
    private float mDepth;
    private boolean mCreateTextureCoords;
    private boolean mCreateVertexColorBuffer;
    private ArrayList<Facet> facets = new ArrayList<>();
    private boolean needUpdateVertexBuffer;
    /**
     * Creates a cube primitive. Calling this constructor will create texture coordinates but no vertex color buffer.
     *
     * @param size The uniform size of the cube.
     */
    public FixtureRectangularPrism(float size) {
        this(size, size, size, false, true, false);
    }

    /**
     * Creates a cube primitive. Calling this constructor will create texture coordinates but no vertex color buffer.
     *
     * @param width  The width of the prism.
     * @param height The height of the prism.
     * @param depth  The depth of the prism.
     */
    public FixtureRectangularPrism(float width, float height, float depth) {
        this(width, height, depth, false, true, false);
    }

    /**
     * Creates a cube primitive. Calling this constructor will create texture coordinates but no vertex color buffer.
     *
     * @param width             The width of the prism.
     * @param height            The height of the prism.
     * @param depth             The depth of the prism.
     * @param hasCubemapTexture A boolean that indicates a cube map texture will be used (6 textures) or a regular
     *                          single texture.
     */
    public FixtureRectangularPrism(float width, float height, float depth, boolean hasCubemapTexture) {
        this(width, height, depth, hasCubemapTexture, true, false);
    }

    /**
     * Creates a cube primitive.
     *
     * @param width                    The width of the prism.
     * @param height                   The height of the prism.
     * @param depth                    The depth of the prism.
     * @param hasCubemapTexture        A boolean that indicates a cube map texture will be used (6 textures) or a regular
     *                                 single texture.
     * @param createTextureCoordinates A boolean that indicates whether the texture coordinates should be calculated or not.
     * @param createVertexColorBuffer  A boolean that indicates whether a vertex color buffer should be created or not.
     */
    public FixtureRectangularPrism(float width, float height, float depth, boolean hasCubemapTexture,
                                   boolean createTextureCoordinates, boolean createVertexColorBuffer) {
        this(width, height, depth, hasCubemapTexture, createTextureCoordinates, createVertexColorBuffer, true);
    }

    /**
     * Creates a cube primitive.
     *
     * @param width                    The width of the prism.
     * @param height                   The height of the prism.
     * @param depth                    The depth of the prism.
     * @param hasCubemapTexture        A boolean that indicates a cube map texture will be used (6 textures) or a regular
     *                                 single texture.
     * @param createTextureCoordinates A boolean that indicates whether the texture coordinates should be calculated or not.
     * @param createVertexColorBuffer  A boolean that indicates whether a vertex color buffer should be created or not.
     * @param createVBOs               A boolean that indicates whether the VBOs should be created imediately or not.
     */
    public FixtureRectangularPrism(float width, float height, float depth, boolean hasCubemapTexture,
                                   boolean createTextureCoordinates, boolean createVertexColorBuffer, boolean createVBOs) {
        super();
        mWidth = width;
        mHeight = height;
        mDepth = depth;
        mHasCubemapTexture = hasCubemapTexture;
        mCreateTextureCoords = createTextureCoordinates;
        mCreateVertexColorBuffer = createVertexColorBuffer;
        init(createVBOs);
    }

    private void init(boolean createVBOs) {
        float halfWidth = mWidth * .5f;
        float halfHeight = mHeight * .5f;
        float halfDepth = mDepth * .5f;
        float[] vertices = {
                // -- back
                halfWidth, halfHeight, halfDepth, -halfWidth, halfHeight, halfDepth,
                -halfWidth, -halfHeight, halfDepth, halfWidth, -halfHeight, halfDepth, // 0-1-halfSize-3 front

                halfWidth, halfHeight, halfDepth, halfWidth, -halfHeight, halfDepth,
                halfWidth, -halfHeight, -halfDepth, halfWidth, halfHeight, -halfDepth,// 0-3-4-5 right
                // -- front
                halfWidth, -halfHeight, -halfDepth, -halfWidth, -halfHeight, -halfDepth,
                -halfWidth, halfHeight, -halfDepth, halfWidth, halfHeight, -halfDepth,// 4-7-6-5 back

                -halfWidth, halfHeight, halfDepth, -halfWidth, halfHeight, -halfDepth,
                -halfWidth, -halfHeight, -halfDepth, -halfWidth, -halfHeight, halfDepth,// 1-6-7-halfSize left

                halfWidth, halfHeight, halfDepth, halfWidth, halfHeight, -halfDepth,
                -halfWidth, halfHeight, -halfDepth, -halfWidth, halfHeight, halfDepth, // 0-5-6-1 top

                halfWidth, -halfHeight, halfDepth, -halfWidth, -halfHeight, halfDepth,
                -halfWidth, -halfHeight, -halfDepth, halfWidth, -halfHeight, -halfDepth,// 3-2-7-4 bottom
        };

        float[] textureCoords = null;
        float[] skyboxTextureCoords = null;

        if (mCreateTextureCoords && !mHasCubemapTexture) {
            textureCoords = new float[]
                    {
                            0, 1, 1, 1, 1, 0, 0, 0, // front
                            0, 1, 1, 1, 1, 0, 0, 0, // up
                            0, 1, 1, 1, 1, 0, 0, 0, // back
                            0, 1, 1, 1, 1, 0, 0, 0, // down
                            0, 1, 1, 1, 1, 0, 0, 0, // right
                            0, 1, 1, 1, 1, 0, 0, 0, // left
                    };
        }

        float[] colors = null;
        if (mCreateVertexColorBuffer) {
            colors = new float[]{
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1
            };
        }

        float n = 1;

        float[] normals = {
                0, 0, n, 0, 0, n, 0, 0, n, 0, 0, n, // front
                n, 0, 0, n, 0, 0, n, 0, 0, n, 0, 0, // right
                0, 0, -n, 0, 0, -n, 0, 0, -n, 0, 0, -n, // back
                -n, 0, 0, -n, 0, 0, -n, 0, 0, -n, 0, 0, // left
                0, n, 0, 0, n, 0, 0, n, 0, 0, n, 0, // top
                0, -n, 0, 0, -n, 0, 0, -n, 0, 0, -n, 0, // bottom
        };

        int[] indices = {
                0, 1, 2, 0, 2, 3,
                4, 5, 6, 4, 6, 7,
                8, 9, 10, 8, 10, 11,
                12, 13, 14, 12, 14, 15,
                16, 17, 18, 16, 18, 19,
                20, 21, 22, 20, 22, 23,
        };

        setData(vertices, GLES20.GL_DYNAMIC_DRAW, normals, GLES20.GL_STATIC_DRAW, textureCoords, GLES20.GL_STATIC_DRAW, colors, GLES20.GL_STATIC_DRAW, indices, GLES20.GL_STATIC_DRAW, false);

        facets.add(new Facet(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, new int[] {0, 1, 2}));
        facets.add(new Facet(new int[] {12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23}, new int[] {12, 13, 14}));
        facets.add(new Facet(new int[] {24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35}, new int[] {24, 25, 26}));
        facets.add(new Facet(new int[] {36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47}, new int[] {36, 37, 38}));
        facets.add(new Facet(new int[] {48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59}, new int[] {48, 49, 50}));
        facets.add(new Facet(new int[] {60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71}, new int[] {60, 61, 62}));


        needUpdateVertexBuffer = false;
        vertices = null;
        normals = null;
        skyboxTextureCoords = null;
        textureCoords = null;
        colors = null;
        indices = null;
    }

    public float getHeight() {
        return mHeight;
    }

    public float getWidth() {
        return mWidth;
    }

    public float getDepth() {
        return mDepth;
    }

    public ArrayList<Facet> getFacets() {
        return facets;
    }

    public boolean needUpdateVertexBuffer() {
        return needUpdateVertexBuffer;
    }

    public void updateVertexBuffer() {
        BufferInfo vertexBufferInfo = getGeometry().getVertexBufferInfo();
        getGeometry().changeBufferData(vertexBufferInfo, getGeometry().getVertices(), 0, true);
        needUpdateVertexBuffer = false;
    }

    public void setWidth(float width) {
        float widthDelta = width - mWidth;
        float widthDeltaHalf = widthDelta * 0.5f;
        for (int i = 0; i < getGeometry().getNumVertices(); i++) {
            int index = i * 3;
            float value = getGeometry().getVertices().get(index);
            if (value > 0.0f) {
                value += widthDeltaHalf;
            } else if (value < 0.0f) {
                value -= widthDeltaHalf;
            }
            getGeometry().getVertices().put(index, value);
        }
        mWidth = width;
        needUpdateVertexBuffer = true;
    }

    public void setHeight(float height) {
        float heightDelta = height - mHeight;
        for (int i = 0; i < TOP_VERTEX_INDEXES.length; i++) {
            int index = TOP_VERTEX_INDEXES[i] * 3 + 1;
            float value = getGeometry().getVertices().get(index);
            value += heightDelta;
            getGeometry().getVertices().put(index, value);
        }
        mHeight = height;
        needUpdateVertexBuffer = true;
    }

    public void setDepth(float depth) {
        float depthDelta = depth - mDepth;
        float depthDeltaHalf = depthDelta * 0.5f;
        for (int i = 0; i < getGeometry().getNumVertices(); i++) {
            int index = i * 3 + 2;
            float value = getGeometry().getVertices().get(index);
            if (value > 0.0f) {
                value += depthDeltaHalf;
            } else if (value < 0.0f) {
                value -= depthDeltaHalf;
            }
            getGeometry().getVertices().put(index, value);
        }
        mDepth = depth;
        needUpdateVertexBuffer = true;
    }
}
