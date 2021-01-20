/*-
 * #%L
 * BigDataViewer quick visualization API.
 * %%
 * Copyright (C) 2016 - 2021 BigDataViewer developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.LongStream;

import net.imglib2.Dimensions;
import net.imglib2.EuclideanSpace;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public enum AxisOrder
{
	XYZ   ( 3, -1, -1, false, false ), // --> XYZ
	XYZC  ( 4,  3, -1, false, false ), // --> XYZ
	XYZT  ( 4, -1,  3, false, false ), // --> XYZT
	XYZCT ( 5,  3,  4, false, false ), // --> XYZT
	XYZTC ( 5,  4,  3, false, false ), // --> XYZT
	XYCZT ( 5,  2,  4, false, false ), // --> XYCZT
	XY    ( 2, -1, -1, true,  false ), // --> XY   --> XYZ
	XYC   ( 3,  2, -1, true,  false ), // --> XY   --> XYZ
	XYT   ( 3, -1,  2, true,  true ),  // --> XYT  --> XYTZ --> XYZT
	XYCT  ( 4,  2,  3, true,  true ),  // --> XYT  --> XYTZ --> XYZT
	XYTC  ( 4,  3,  2, true,  true ),  // --> XYT  --> XYTZ --> XYZT
	XYCZ  ( 4,  2, -1, false, false ), // --> XYCZ
	DEFAULT( 0, 0,  0, true,  true );

	final int numDimensions;

	final int channelDimension;

	final int timeDimension;

	final boolean addZ;

	final boolean flipZ;

	private AxisOrder(
			final int numDimensions,
			final int channelDimension,
			final int timeDimension,
			final boolean addZ,
			final boolean flipZ )
	{
		this.numDimensions = numDimensions;
		this.channelDimension = channelDimension;
		this.timeDimension = timeDimension;
		this.addZ = addZ;
		this.flipZ = flipZ;
	}

	public static AxisOrder getAxisOrder( final AxisOrder axisOrder, final EuclideanSpace space, final boolean viewerIs2D )
	{
		if ( axisOrder == DEFAULT )
		{
			if ( viewerIs2D )
			{
				switch ( space.numDimensions() )
				{
				case 2:
					return XY;
				case 3:
					return XYT;
				case 4:
					return XYTC;
				case 5:
					return XYZTC;
				}
			}
			else
			{
				switch ( space.numDimensions() )
				{
				case 2:
					return XY;
				case 3:
					return XYZ;
				case 4:
					return XYZT;
				case 5:
					return XYZTC;
				}
			}
			throw new IllegalArgumentException( "image dimensionality " + space.numDimensions() + " is not supported" );
		}
		return axisOrder;
	}

	public static < T > ArrayList< RandomAccessibleInterval< T > > splitInputStackIntoSourceStacks(
			final RandomAccessibleInterval< T > img,
			final AxisOrder axisOrder )
	{
		if ( img.numDimensions() != axisOrder.numDimensions )
			throw new IllegalArgumentException( "provided AxisOrder doesn't match dimensionality of image" );

		final ArrayList< RandomAccessibleInterval< T > > sourceStacks = new ArrayList< >();

		/*
		 * If there a channels dimension, slice img along that dimension.
		 */
		final int c = axisOrder.channelDimension;
		if ( c != -1 )
		{
			final int numSlices = ( int ) img.dimension( c );
			for ( int s = 0; s < numSlices; ++s )
				sourceStacks.add( Views.hyperSlice( img, c, s + img.min( c ) ) );
		}
		else
			sourceStacks.add( img );

		/*
		 * If AxisOrder is a 2D variant (has no Z dimension), augment the
		 * sourceStacks by a Z dimension.
		 */
		if ( axisOrder.addZ )
			for ( int i = 0; i < sourceStacks.size(); ++i )
				sourceStacks.set( i, Views.addDimension( sourceStacks.get( i ), 0, 0 ) );

		/*
		 * If at this point the dim order is XYTZ, permute to XYZT
		 */
		if ( axisOrder.flipZ )
			for ( int i = 0; i < sourceStacks.size(); ++i )
				sourceStacks.set( i, Views.permute( sourceStacks.get( i ), 2, 3 ) );

		return sourceStacks;
	}

	public static < T > Pair< ArrayList< RandomAccessible< T > >, Interval > splitInputStackIntoSourceStacks(
			final RandomAccessible< T > img,
			Interval interval,
			final AxisOrder axisOrder )
	{
		if ( img.numDimensions() != axisOrder.numDimensions )
			throw new IllegalArgumentException( "provided AxisOrder doesn't match dimensionality of image" );

		final ArrayList< RandomAccessible< T > > sourceStacks = new ArrayList<>();

		/*
		 * If there a channels dimension, slice img along that dimension.
		 */
		final int c = axisOrder.channelDimension;
		if ( c != -1 )
		{
			final long[] min = new long[ interval.numDimensions() -1 ];
			final long[] max = new long[ interval.numDimensions() -1 ];
			for ( int dim = 0; dim < min.length; ++dim ) {
				final int otherIndex = dim >= axisOrder.channelDimension ? dim + 1 : dim;
				min[ dim ] = interval.min( otherIndex );
				max[ dim ] = interval.max( otherIndex );
			}
			interval = new FinalInterval( min, max );
			final int numSlices = ( int ) interval.dimension( c );
			for ( int s = 0; s < numSlices; ++s )
				sourceStacks.add( Views.hyperSlice( img, c, s + interval.min( c ) ) );
		}
		else
			sourceStacks.add( img );

		/*
		 * If AxisOrder is a 2D variant (has no Z dimension), augment the
		 * sourceStacks by a Z dimension.
		 */
		if ( axisOrder.addZ ) {
			final long[] min = LongStream.concat( Arrays.stream( Intervals.minAsLongArray( interval ) ), LongStream.of( 0 ) ).toArray();
			final long[] max = LongStream.concat( Arrays.stream( Intervals.maxAsLongArray( interval ) ), LongStream.of( 0 ) ).toArray();
			interval = new FinalInterval( min, max );
			for ( int i = 0; i < sourceStacks.size(); ++i )
				sourceStacks.set( i, Views.addDimension( sourceStacks.get( i ) ) );
		}

		/*
		 * If at this point the dim order is XYTZ, permute to XYZT
		 */
		if ( axisOrder.flipZ )
		{
			final long[] min = Intervals.minAsLongArray( interval );
			final long[] max = Intervals.maxAsLongArray( interval );
			final long minTmp = min[ 3 ];
			final long maxTmp = max[ 3 ];
			min[ 3 ] = min[ 2 ];
			max[ 3 ] = max[ 2 ];
			min[ 2 ] = minTmp;
			max[ 2 ] = maxTmp;
			interval = new FinalInterval( min, max );
			for ( int i = 0; i < sourceStacks.size(); ++i )
				sourceStacks.set( i, Views.permute( sourceStacks.get( i ), 2, 3 ) );
		}

		return new ValuePair<>( sourceStacks, interval );
	}

	public int channelDimension()
	{
		return channelDimension;
	}

	public boolean hasChannels()
	{
		return channelDimension >= 0;
	}

	public long numChannels( Dimensions dimensions )
	{
		return hasChannels() ? dimensions.dimension( channelDimension ) : 1;
	}

	public int timeDimension()
	{
		return timeDimension;
	}

	public boolean hasTimepoints()
	{
		return timeDimension >= 0;
	}

	public long numTimepoints( Dimensions dimensions )
	{
		return hasTimepoints() ? dimensions.dimension( timeDimension ) : 1;
	}
}
