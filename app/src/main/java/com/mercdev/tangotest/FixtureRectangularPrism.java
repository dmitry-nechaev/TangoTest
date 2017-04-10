package com.mercdev.tangotest;

import org.rajawali3d.Object3D;
import org.rajawali3d.math.vector.Vector3;

import java.util.ArrayList;

/**
 * Created by nechaev on 02.03.2017.
 */

public class FixtureRectangularPrism extends Object3D {

    public class Facet {
        private Vector3[] vertices;
        private Vector3 normal;

        public Facet(Vector3[] vertices, Vector3 normal) {
            this.vertices = vertices;
            this.normal = normal;
        }

        public Vector3 getNormal() {
            return normal;
        }

        public Vector3[] getVertices() {
            return vertices;
        }
    }

    private float mWidth;
    private float mHeight;
    private float mDepth;
    private boolean mCreateTextureCoords;
    private boolean mCreateVertexColorBuffer;
    private ArrayList<Facet> facets = new ArrayList<>();

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

        setData(vertices, normals, textureCoords, colors, indices, createVBOs);

        for (int i = 0; i < vertices.length; i = i + 12) {
            Vector3 vertex_1 = new Vector3(vertices[i], vertices[i + 1], vertices[i + 2]);
            Vector3 vertex_2 = new Vector3(vertices[i + 3], vertices[i + 4], vertices[i + 5]);
            Vector3 vertex_3 = new Vector3(vertices[i + 6], vertices[i + 7], vertices[i + 8]);
            Vector3 vertex_4 = new Vector3(vertices[i + 9], vertices[i + 10], vertices[i + 11]);
            Vector3 normal = new Vector3(normals[i], normals[i + 1], normals[i + 2]);

            Vector3[] facetVertices = {vertex_1, vertex_2, vertex_3, vertex_4};
            facets.add(new Facet(facetVertices, normal));
        }

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

    public double getDistanceFromPoint(Vector3 position) {
        double x1 = getX() - getWidth() * 0.5f;
        double y1 = getY() + getHeight() * 0.5f;
        double z1 = getZ() - getDepth() * 0.5f;

        double x2 = getX() + getWidth() * 0.5f;
        double y2 = getY() - getHeight() * 0.5f;
        double z2 = getZ() + getDepth() * 0.5f;

        double x = position.x;
        double y = position.y;
        double z = position.z;

        if (Double.compare(x, x1) < 0) {
            x = x1;
        } else if (Double.compare(x, x2) > 0) {
            x = x2;
        }

        if (Double.compare(y, y1) > 0) {
            y = y1;
        } else if (Double.compare(y, y2) < 0) {
            y = y2;
        }

        if (Double.compare(z, z1) < 0) {
            z = z1;
        } else if (Double.compare(z, z2) > 0) {
            z = z2;
        }

        double distance = Math.sqrt(Math.pow(x - position.x, 2) + Math.pow(y - position.y, 2) + Math.pow(z - position.z, 2));
        return distance;
    }
}
