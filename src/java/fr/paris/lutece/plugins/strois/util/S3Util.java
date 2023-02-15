/*
 * Copyright (c) 2002-2023, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
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
 *
 * License 1.0
 */

package fr.paris.lutece.plugins.strois.util;

public class S3Util
{
    private S3Util( )
    {

    }

    public static boolean matchesList( String [ ] listPatterns, String strText )
    {
        if ( listPatterns != null )
        {
            for ( String pattern : listPatterns )
            {
                if ( matches( pattern, strText ) )
                {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean matches( String strPattern, String strText )
    {
        String strTextTmp = strText + '\u0000';
        String strPatternTmp = strPattern + '\u0000';
        int nLength = strPatternTmp.length( );
        boolean [ ] states = new boolean [ nLength + 1];
        boolean [ ] old = new boolean [ nLength + 1];
        old [0] = true;

        for ( int i = 0; i < strTextTmp.length( ); ++i )
        {
            char c = strTextTmp.charAt( i );
            states = new boolean [ nLength + 1];

            for ( int j = 0; j < nLength; ++j )
            {
                char p = strPatternTmp.charAt( j );
                if ( old [j] && p == '*' )
                {
                    old [j + 1] = true;
                }

                if ( old [j] && p == c )
                {
                    states [j + 1] = true;
                }

                if ( old [j] && p == '?' )
                {
                    states [j + 1] = true;
                }

                if ( old [j] && p == '*' )
                {
                    states [j] = true;
                }

                if ( old [j] && p == '*' )
                {
                    states [j + 1] = true;
                }
            }

            old = states;
        }

        return states [nLength];
    }
}
