/**
* Copyright 2016 Bartosz Schiller
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.github.barteksc.pdfviewer;

import android.app.AlertDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.text.InputType;
import android.widget.EditText;

import com.github.barteksc.pdfviewer.source.DocumentSource;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import com.shockwave.pdfium.util.Size;

import java.lang.ref.WeakReference;

class DecodingAsyncTask extends AsyncTask<Void, Void, Throwable> {

    private boolean cancelled;
    private WeakReference<PDFView> pdfViewReference;
    private PdfiumCore pdfiumCore;
    private String password;
    private DocumentSource docSource;
    private int[] userPages;
    private PdfFile pdfFile;
    private int attempts = 0;
    private static final String[] DEFAULT_PASSWORDS = {"", "toonbatch.web.id"};
    private boolean manualRetry = false;

    DecodingAsyncTask(DocumentSource docSource, String password, int[] userPages, PDFView pdfView, PdfiumCore pdfiumCore) {
        this.docSource = docSource;
        this.userPages = userPages;
        this.cancelled = false;
        this.pdfViewReference = new WeakReference<>(pdfView);
        this.password = password;
        this.pdfiumCore = pdfiumCore;
        this.attempts = password == null ? 0 : 2;
    }

    @Override
    protected Throwable doInBackground(Void... params) {
        try {
            PDFView pdfView = pdfViewReference.get();
            if (pdfView != null) {
                String pass = password != null ? password : DEFAULT_PASSWORDS[attempts];

                PdfDocument pdfDocument = docSource.createDocument(pdfView.getContext(), pdfiumCore, pass);
                pdfFile = new PdfFile(pdfiumCore, pdfDocument, pdfView.getPageFitPolicy(), getViewSize(pdfView),
                        userPages, pdfView.isSwipeVertical(), pdfView.getSpacingPx(), pdfView.isAutoSpacingEnabled(),
                        pdfView.isFitEachPage());
                return null;
            } else {
                return new NullPointerException("pdfView == null");
            }

        } catch (Throwable t) {
            return t;
        }
    }

    private Size getViewSize(PDFView pdfView) {
        return new Size(pdfView.getWidth(), pdfView.getHeight());
    }

    @Override
    protected void onPostExecute(Throwable t) {
        PDFView pdfView = pdfViewReference.get();
        if (pdfView != null) {
            if (t != null) {
                if (t.getMessage() != null) {
                    if (attempts < 2) {
                        DecodingAsyncTask retryTask = new DecodingAsyncTask(docSource, null, userPages, pdfView, pdfiumCore);
                        retryTask.attempts = this.attempts + 1;
                        retryTask.execute();
                    } else {
                        showPasswordDialog(pdfView);
                    }
                } else {
                    pdfView.loadError(t);
                }
                return;
            }
            if (!cancelled) {
                pdfView.loadComplete(pdfFile);
            }
        }
    }

    @Override
    protected void onCancelled() {
        cancelled = true;
    }

    private void showPasswordDialog(PDFView pdfView) {
        Context context = pdfView.getContext();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(manualRetry ? "Password salah! Masukkan ulang?" : "Masukkan Password:");
        builder.setCancelable(false);

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String newPassword = input.getText().toString();
            DecodingAsyncTask task = new DecodingAsyncTask(docSource, newPassword, userPages, pdfView, pdfiumCore);
            task.manualRetry = true;
            task.execute();
        });

        builder.setNegativeButton("Batal", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.show();
    }
}
