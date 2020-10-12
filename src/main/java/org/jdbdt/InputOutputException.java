/*
 * The MIT License
 *
 * Copyright (c) Eduardo R. B. Marques
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.jdbdt;

import java.io.IOException;

/**
 * Exception thrown due to an I/O exception 
 * (unchecked wrapper for a <code>java.io.IOException</code>).
 * 
 * @since 1.3
 */
public class InputOutputException extends JDBDTRuntimeException {
  /**
   * Constructor with supplied cause.
   * @param cause Cause.
   */
  public InputOutputException(IOException cause) {
    super("I/O error", cause);
  }
  
  /**
   * Constructor with supplied message and cause.
   * @param message Message.
   * @param cause Original cause of exception.
   */
  public InputOutputException(String message, IOException cause) {
    super(message, cause);
  }

 
  @SuppressWarnings("javadoc")
  private static final long serialVersionUID = 1L;

}